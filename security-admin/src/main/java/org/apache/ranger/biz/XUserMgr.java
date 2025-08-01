/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.biz;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.util.Time;
import org.apache.ranger.biz.ServiceDBStore.REMOVE_REF_TYPE;
import org.apache.ranger.common.AppConstants;
import org.apache.ranger.common.ContextUtil;
import org.apache.ranger.common.MessageEnums;
import org.apache.ranger.common.PropertiesUtil;
import org.apache.ranger.common.RangerCommonEnums;
import org.apache.ranger.common.RangerConstants;
import org.apache.ranger.common.RangerServicePoliciesCache;
import org.apache.ranger.common.RangerUserStoreCache;
import org.apache.ranger.common.SearchCriteria;
import org.apache.ranger.common.StringUtil;
import org.apache.ranger.common.UserSessionBase;
import org.apache.ranger.common.db.RangerTransactionSynchronizationAdapter;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.db.XXAuditMapDao;
import org.apache.ranger.db.XXAuthSessionDao;
import org.apache.ranger.db.XXGroupDao;
import org.apache.ranger.db.XXGroupPermissionDao;
import org.apache.ranger.db.XXGroupUserDao;
import org.apache.ranger.db.XXPermMapDao;
import org.apache.ranger.db.XXPolicyDao;
import org.apache.ranger.db.XXPortalUserDao;
import org.apache.ranger.db.XXPortalUserRoleDao;
import org.apache.ranger.db.XXResourceDao;
import org.apache.ranger.db.XXUserDao;
import org.apache.ranger.db.XXUserPermissionDao;
import org.apache.ranger.entity.XXAuditMap;
import org.apache.ranger.entity.XXGroup;
import org.apache.ranger.entity.XXGroupPermission;
import org.apache.ranger.entity.XXGroupUser;
import org.apache.ranger.entity.XXModuleDef;
import org.apache.ranger.entity.XXPermMap;
import org.apache.ranger.entity.XXPolicy;
import org.apache.ranger.entity.XXPortalUser;
import org.apache.ranger.entity.XXPortalUserRole;
import org.apache.ranger.entity.XXResource;
import org.apache.ranger.entity.XXRole;
import org.apache.ranger.entity.XXRoleRefGroup;
import org.apache.ranger.entity.XXRoleRefUser;
import org.apache.ranger.entity.XXSecurityZone;
import org.apache.ranger.entity.XXSecurityZoneRefGroup;
import org.apache.ranger.entity.XXSecurityZoneRefUser;
import org.apache.ranger.entity.XXTrxLogV2;
import org.apache.ranger.entity.XXUser;
import org.apache.ranger.entity.XXUserPermission;
import org.apache.ranger.plugin.model.GroupInfo;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerPolicy.RangerDataMaskPolicyItem;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyItem;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyResource;
import org.apache.ranger.plugin.model.RangerPolicy.RangerRowFilterPolicyItem;
import org.apache.ranger.plugin.model.RangerPrincipal;
import org.apache.ranger.plugin.model.UserInfo;
import org.apache.ranger.plugin.store.EmbeddedServiceDefsUtil;
import org.apache.ranger.plugin.util.PasswordUtils.PasswordGenerator;
import org.apache.ranger.plugin.util.RangerUserStore;
import org.apache.ranger.service.RangerPolicyService;
import org.apache.ranger.service.XPortalUserService;
import org.apache.ranger.service.XResourceService;
import org.apache.ranger.service.XUgsyncAuditInfoService;
import org.apache.ranger.ugsyncutil.model.GroupUserInfo;
import org.apache.ranger.ugsyncutil.model.UsersGroupRoleAssignments;
import org.apache.ranger.view.VXAuditMap;
import org.apache.ranger.view.VXAuditMapList;
import org.apache.ranger.view.VXGroup;
import org.apache.ranger.view.VXGroupList;
import org.apache.ranger.view.VXGroupPermission;
import org.apache.ranger.view.VXGroupUser;
import org.apache.ranger.view.VXGroupUserInfo;
import org.apache.ranger.view.VXGroupUserList;
import org.apache.ranger.view.VXLong;
import org.apache.ranger.view.VXModuleDef;
import org.apache.ranger.view.VXPermMap;
import org.apache.ranger.view.VXPermMapList;
import org.apache.ranger.view.VXPortalUser;
import org.apache.ranger.view.VXResponse;
import org.apache.ranger.view.VXString;
import org.apache.ranger.view.VXStringList;
import org.apache.ranger.view.VXUgsyncAuditInfo;
import org.apache.ranger.view.VXUser;
import org.apache.ranger.view.VXUserGroupInfo;
import org.apache.ranger.view.VXUserList;
import org.apache.ranger.view.VXUserPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.apache.ranger.db.XXGlobalStateDao.RANGER_GLOBAL_STATE_NAME_USER_GROUP;
import static org.apache.ranger.service.RangerBaseModelService.OPERATION_CREATE_CONTEXT;
import static org.apache.ranger.service.RangerBaseModelService.OPERATION_DELETE_CONTEXT;
import static org.apache.ranger.service.RangerBaseModelService.OPERATION_UPDATE_CONTEXT;

@Component
public class XUserMgr extends XUserMgrBase {
    static final Logger logger = LoggerFactory.getLogger(XUserMgr.class);

    static final Set<String> roleAssignmentUpdatedUsers = new HashSet<>();
    static final String      MSG_DATA_ACCESS_DENY       = "Logged-In user is not allowed to access requested user data";

    private static final String USER                       = "User";
    private static final String GROUP                      = "Group";
    private static final int    MAX_DB_TRANSACTION_RETRIES = 5;
    private static final int    PASSWORD_LENGTH            = 16;

    @Autowired
    RangerBizUtil msBizUtil;

    @Autowired
    UserMgr userMgr;

    @Autowired
    RangerDaoManager daoManager;

    @Autowired
    RangerBizUtil xaBizUtil;

    @Autowired
    XPortalUserService xPortalUserService;

    @Autowired
    XResourceService xResourceService;

    @Autowired
    SessionMgr sessionMgr;

    @Autowired
    RangerPolicyService policyService;

    @Autowired
    ServiceDBStore svcStore;

    @Autowired
    XUgsyncAuditInfoService xUgsyncAuditInfoService;

    @Autowired
    StringUtil stringUtil;

    @Autowired
    RangerTransactionSynchronizationAdapter transactionSynchronizationAdapter;

    @Autowired
    GdsDBStore gdsStore;

    @Autowired
    @Qualifier(value = "transactionManager")

    PlatformTransactionManager txManager;

    public VXUser getXUserByUserName(String userName) {
        VXUser vXUser = xUserService.getXUserByUserName(userName);

        if (vXUser != null && !hasAccessToGetUserInfo(vXUser)) {
            logger.info(MSG_DATA_ACCESS_DENY);

            throw restErrorUtil.createRESTException(HttpServletResponse.SC_FORBIDDEN, MSG_DATA_ACCESS_DENY, true);
        }

        if (vXUser != null && !hasAccessToModule(RangerConstants.MODULE_USER_GROUPS)) {
            vXUser = getMaskedVXUser(vXUser);
        }

        return vXUser;
    }

    public VXGroup getGroupByGroupName(String groupName) {
        VXGroup vxGroup = xGroupService.getGroupByGroupName(groupName);

        if (vxGroup == null) {
            throw restErrorUtil.createRESTException(groupName + " is Not Found", MessageEnums.DATA_NOT_FOUND);
        }

        return vxGroup;
    }

    public void assignPermissionToUser(VXPortalUser vXPortalUser, boolean isCreate) {
        HashMap<String, Long> moduleNameId = getAllModuleNameAndIdMap();

        if (moduleNameId != null && vXPortalUser != null && CollectionUtils.isNotEmpty(vXPortalUser.getUserRoleList())) {
            for (String role : vXPortalUser.getUserRoleList()) {
                if (RangerConstants.VALID_USER_ROLE_LIST.contains(role)) {
                    createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_RESOURCE_BASED_POLICIES), isCreate);
                    createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_REPORTS), isCreate);

                    if (role.equals(RangerConstants.ROLE_USER)) {
                        createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_SECURITY_ZONE), isCreate);
                    } else {
                        createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_AUDIT), isCreate);
                        createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_USER_GROUPS), isCreate);
                        createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_GOVERNED_DATA_SHARING), isCreate);

                        if (role.equals(RangerConstants.ROLE_SYS_ADMIN) || role.equals(RangerConstants.ROLE_ADMIN_AUDITOR)) {
                            createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_TAG_BASED_POLICIES), isCreate);
                            createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_SECURITY_ZONE), isCreate);
                        } else {
                            createOrUpdateUserPermisson(vXPortalUser, moduleNameId.get(RangerConstants.MODULE_KEY_MANAGER), isCreate);
                        }
                    }
                }
            }
        }
    }

    // Insert or Updating Mapping permissions depending upon roles
    public void createOrUpdateUserPermisson(VXPortalUser portalUser, Long moduleId, boolean isCreate) {
        VXUserPermission vXUserPermission;
        XXUserPermission xUserPermission = daoManager.getXXUserPermission().findByModuleIdAndPortalUserId(portalUser.getId(), moduleId);

        if (xUserPermission == null) {
            vXUserPermission = new VXUserPermission();

            // When Creating XXUserPermission UI sends xUserId, to keep it consistent here xUserId should be used
            XXUser xUser = daoManager.getXXUser().findByPortalUserId(portalUser.getId());

            if (xUser == null) {
                logger.warn("Could not found corresponding xUser for username: [{}], So not assigning permission to this user", portalUser.getLoginId());

                return;
            } else {
                vXUserPermission.setUserId(xUser.getId());
            }

            vXUserPermission.setIsAllowed(RangerCommonEnums.IS_ALLOWED);
            vXUserPermission.setModuleId(moduleId);

            try {
                vXUserPermission = this.createXUserPermission(vXUserPermission);

                logger.info("Permission assigned to user: [{}] For Module: [{}]", vXUserPermission.getUserName(), vXUserPermission.getModuleName());
            } catch (Exception e) {
                logger.error("Error while assigning permission to user: [{}] for module: [{}]", portalUser.getLoginId(), moduleId, e);
            }
        } else if (isCreate) {
            vXUserPermission = xUserPermissionService.populateViewBean(xUserPermission);

            vXUserPermission.setIsAllowed(RangerCommonEnums.IS_ALLOWED);

            vXUserPermission = this.updateXUserPermission(vXUserPermission);

            logger.info("Permission Updated for user: [{}] For Module: [{}]", vXUserPermission.getUserName(), vXUserPermission.getModuleName());
        }
    }

    public HashMap<String, Long> getAllModuleNameAndIdMap() {
        List<XXModuleDef> xXModuleDefs = daoManager.getXXModuleDef().getAll();

        if (!CollectionUtils.isEmpty(xXModuleDefs)) {
            HashMap<String, Long> moduleNameAndIdMap = new HashMap<>();

            for (XXModuleDef xXModuleDef : xXModuleDefs) {
                moduleNameAndIdMap.put(xXModuleDef.getModule(), xXModuleDef.getId());
            }

            return moduleNameAndIdMap;
        }

        return null;
    }

    public VXUserGroupInfo createXUserGroupFromMap(VXUserGroupInfo vXUserGroupInfo) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        VXUserGroupInfo    vxUGInfo     = new VXUserGroupInfo();
        VXUser             vXUser       = vXUserGroupInfo.getXuserInfo();
        VXPortalUser       vXPortalUser = userMgr.getUserProfileByLoginId(vXUser.getName());
        XXPortalUser       xxPortalUser = daoManager.getXXPortalUser().findByLoginId(vXUser.getName());
        Collection<String> reqRoleList  = vXUser.getUserRoleList();
        List<String>       existingRole = daoManager.getXXPortalUserRole().findXPortalUserRolebyXPortalUserId(xxPortalUser.getId());

        if (xxPortalUser.getUserSource() == RangerCommonEnums.USER_EXTERNAL) {
            vXPortalUser = userMgr.updateRoleForExternalUsers(reqRoleList, existingRole, vXPortalUser);
        }

        vXUser = xUserService.createXUserWithOutLogin(vXUser);

        vxUGInfo.setXuserInfo(vXUser);

        List<VXGroup> vxg = new ArrayList<>();

        for (VXGroup vXGroup : vXUserGroupInfo.getXgroupInfo()) {
            VXGroup vvXGroup = xGroupService.createXGroupWithOutLogin(vXGroup);

            vxg.add(vvXGroup);

            VXGroupUser vXGroupUser = new VXGroupUser();

            vXGroupUser.setUserId(vXUser.getId());
            vXGroupUser.setName(vvXGroup.getName());

            xGroupUserService.createXGroupUserWithOutLogin(vXGroupUser);
        }

        if (vXPortalUser != null) {
            assignPermissionToUser(vXPortalUser, true);
        }

        vxUGInfo.setXgroupInfo(vxg);

        updateUserStoreVersion("createXUserGroupFromMap(" + vXUser.getName() + ")");

        return vxUGInfo;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED)
    public VXGroupUserInfo createXGroupUserFromMap(VXGroupUserInfo vXGroupUserInfo) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        VXGroupUserInfo vxGUInfo = new VXGroupUserInfo();
        VXGroup         vXGroup  = vXGroupUserInfo.getXgroupInfo();

        // Add the group user mappings for a given group to x_group_user table
        /*XXGroup xGroup = daoManager.getXXGroup().findByGroupName(vXGroup.getName());
        if (xGroup == null) {
            return vxGUInfo;
        }*/

        List<VXUser> vxu = new ArrayList<>();

        for (VXUser vXUser : vXGroupUserInfo.getXuserInfo()) {
            XXUser       xUser        = daoManager.getXXUser().findByUserName(vXUser.getName());
            XXPortalUser xXPortalUser = daoManager.getXXPortalUser().findByLoginId(vXUser.getName());

            if (xUser != null) {
                // Add or update group user mapping only if the user already exists in x_user table.
                logger.debug("createXGroupUserFromMap(): Create or update group {}", vXGroup.getName());

                vXGroup = xGroupService.createXGroupWithOutLogin(vXGroup);

                vxGUInfo.setXgroupInfo(vXGroup);
                vxu.add(vXUser);

                VXGroupUser vXGroupUser = new VXGroupUser();

                vXGroupUser.setUserId(xUser.getId());
                vXGroupUser.setName(vXGroup.getName());

                if (xXPortalUser.getUserSource() == RangerCommonEnums.USER_EXTERNAL) {
                    xGroupUserService.createXGroupUserWithOutLogin(vXGroupUser);

                    logger.debug("createXGroupUserFromMap(): Create or update group user mapping with groupname = {} username = {} userId = {}", vXGroup.getName(), xXPortalUser.getLoginId(), xUser.getId());
                }

                Collection<String> reqRoleList  = vXUser.getUserRoleList();
                XXPortalUser       xxPortalUser = daoManager.getXXPortalUser().findByLoginId(vXUser.getName());
                List<String>       existingRole = daoManager.getXXPortalUserRole().findXPortalUserRolebyXPortalUserId(xxPortalUser.getId());
                VXPortalUser       vxPortalUser = userMgr.mapXXPortalUserToVXPortalUserForDefaultAccount(xxPortalUser);

                if (xxPortalUser.getUserSource() == RangerCommonEnums.USER_EXTERNAL) {
                    vxPortalUser = userMgr.updateRoleForExternalUsers(reqRoleList, existingRole, vxPortalUser);

                    assignPermissionToUser(vxPortalUser, true);
                }
            }
        }

        vxGUInfo.setXuserInfo(vxu);

        updateUserStoreVersion("createXGroupUserFromMap(" + vXGroup.getName() + ")");

        return vxGUInfo;
    }

    public VXGroupUserInfo getXGroupUserFromMap(String groupName) {
        checkAdminAccess();

        VXGroupUserInfo vxGUInfo = new VXGroupUserInfo();
        XXGroup         xGroup   = daoManager.getXXGroup().findByGroupName(groupName);

        if (xGroup == null) {
            return vxGUInfo;
        }

        VXGroup xgroupInfo = xGroupService.populateViewBean(xGroup);

        vxGUInfo.setXgroupInfo(xgroupInfo);

        SearchCriteria searchCriteria = new SearchCriteria();

        searchCriteria.addParam("xGroupId", xGroup.getId());

        VXGroupUserList vxGroupUserList = searchXGroupUsers(searchCriteria);
        List<VXUser>    vxu             = new ArrayList<>();

        logger.debug("removing all the group user mapping for : {}", xGroup.getName());

        for (VXGroupUser groupUser : vxGroupUserList.getList()) {
            XXUser xUser = daoManager.getXXUser().getById(groupUser.getUserId());

            if (xUser != null) {
                VXUser vxUser = new VXUser();

                vxUser.setName(xUser.getName());

                XXPortalUser xXPortalUser = daoManager.getXXPortalUser().findByLoginId(xUser.getName());

                if (xXPortalUser != null) {
                    List<String> existingRole = daoManager.getXXPortalUserRole().findXPortalUserRolebyXPortalUserId(xXPortalUser.getId());

                    if (existingRole != null) {
                        vxUser.setUserRoleList(existingRole);
                    }
                }

                vxu.add(vxUser);
            }
        }

        vxGUInfo.setXuserInfo(vxu);

        return vxGUInfo;
    }

    public VXUser createXUserWithOutLogin(VXUser vXUser) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        validatePassword(vXUser);

        VXUser ret = xUserService.createXUserWithOutLogin(vXUser);

        updateUserStoreVersion("createXUserWithOutLogin(" + vXUser.getName() + ")");

        return ret;
    }

    public VXUser createExternalUser(String userName) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        return createServiceConfigUser(userName);
    }

    public VXGroup createXGroupWithoutLogin(VXGroup vXGroup) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        VXGroup ret = xGroupService.createXGroupWithOutLogin(vXGroup);

        updateUserStoreVersion("createXGroupWithoutLogin(" + vXGroup.getName() + ")");

        return ret;
    }

    public VXGroup getXGroup(Long id) {
        UserSessionBase userSession = ContextUtil.getCurrentUserSession();

        if (userSession != null && userSession.getLoginId() != null) {
            VXUser loggedInVXUser = xUserService.getXUserByUserName(userSession.getLoginId());

            if (loggedInVXUser != null) {
                if (loggedInVXUser.getUserRoleList().size() == 1 && loggedInVXUser.getUserRoleList().contains(RangerConstants.ROLE_USER)) {
                    List<Long> listGroupId = daoManager.getXXGroupUser().findGroupIdListByUserId(loggedInVXUser.getId());

                    if (!listGroupId.contains(id)) {
                        logger.info(MSG_DATA_ACCESS_DENY);

                        throw restErrorUtil.create403RESTException("Logged-In user is not allowed to access requested group data.");
                    }
                }
            }
        }

        VXGroup vXGroup = xGroupService.readResourceWithOutLogin(id);

        if (vXGroup != null && !hasAccessToModule(RangerConstants.MODULE_USER_GROUPS)) {
            vXGroup = getMaskedVXGroup(vXGroup);
        }

        return vXGroup;
    }

    public VXGroup createXGroup(VXGroup vXGroup) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        if (vXGroup.getDescription() == null) {
            vXGroup.setDescription(vXGroup.getName());
        }

        vXGroup = xGroupService.createResource(vXGroup);

        xGroupService.createTransactionLog(vXGroup, null, OPERATION_CREATE_CONTEXT);

        updateUserStoreVersion("createXGroup(" + vXGroup.getName() + ")");

        return vXGroup;
    }

    @Override
    public VXGroup updateXGroup(VXGroup vXGroup) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        XXGroup xGroup = daoManager.getXXGroup().getById(vXGroup.getId());

        if (xGroup != null && !vXGroup.getName().equals(xGroup.getName())) {
            throw restErrorUtil.createRESTException("group name updates are not allowed.", MessageEnums.INVALID_INPUT_DATA);
        }

        VXGroup          existing   = xGroup != null ? xGroupService.populateViewBean(xGroup) : null;
        List<XXTrxLogV2> trxLogList = xGroupService.getTransactionLog(vXGroup, existing, OPERATION_UPDATE_CONTEXT);

        xaBizUtil.createTrxLog(trxLogList);

        vXGroup = xGroupService.updateResource(vXGroup);

        if (vXGroup != null) {
            updateXgroupUserForGroupUpdate(vXGroup);

            RangerServicePoliciesCache.sInstance = null;
        }

        return vXGroup;
    }

    public void deleteXGroup(Long id, boolean force) {
        checkAdminAccess();

        blockIfZoneGroup(id);

        this.blockIfRoleGroup(id);

        xaBizUtil.blockAuditorRoleUser();

        XXGroupDao xXGroupDao = daoManager.getXXGroup();
        XXGroup    xXGroup    = xXGroupDao.getById(id);

        if (xXGroup == null) {
            throw restErrorUtil.create404RESTException("Data Not Found for given Id", MessageEnums.DATA_NOT_FOUND, id, null, "readResource : No Object found with given id.");
        }

        VXGroup vXGroup = xGroupService.populateViewBean(xXGroup);

        if (vXGroup == null || StringUtils.isEmpty(vXGroup.getName())) {
            throw restErrorUtil.createRESTException("Group ID doesn't exist.", MessageEnums.INVALID_INPUT_DATA);
        }

        logger.debug("Force delete status={} for group={}", force, vXGroup.getName());

        SearchCriteria searchCriteria = new SearchCriteria();

        searchCriteria.addParam("xGroupId", id);

        VXGroupUserList vxGroupUserList = searchXGroupUsers(searchCriteria);

        searchCriteria = new SearchCriteria();

        searchCriteria.addParam("groupId", id);

        VXPermMapList vXPermMapList = searchXPermMaps(searchCriteria);

        searchCriteria = new SearchCriteria();

        searchCriteria.addParam("groupId", id);

        VXAuditMapList          vXAuditMapList       = searchXAuditMaps(searchCriteria);
        XXGroupPermissionDao    xXGroupPermissionDao = daoManager.getXXGroupPermission();
        List<XXGroupPermission> xXGroupPermissions   = xXGroupPermissionDao.findByGroupId(id);
        XXPolicyDao             xXPolicyDao          = daoManager.getXXPolicy();
        List<XXPolicy>          xXPolicyList         = xXPolicyDao.findByGroupId(id);

        logger.warn("Deleting GROUP : {}", vXGroup.getName());

        if (force) {
            //delete XXGroupUser records of matching group
            XXGroupUserDao xGroupUserDao = daoManager.getXXGroupUser();
            XXUserDao      xXUserDao     = daoManager.getXXUser();

            for (VXGroupUser groupUser : vxGroupUserList.getList()) {
                if (groupUser != null) {
                    XXUser xXUser = xXUserDao.getById(groupUser.getUserId());

                    if (xXUser != null) {
                        logger.warn("Removing user '{}' from group '{}'", xXUser.getName(), groupUser.getName());
                    }

                    xGroupUserDao.remove(groupUser.getId());
                }
            }

            //delete XXPermMap records of matching group
            XXPermMapDao  xXPermMapDao  = daoManager.getXXPermMap();
            XXResourceDao xXResourceDao = daoManager.getXXResource();

            for (VXPermMap vXPermMap : vXPermMapList.getList()) {
                if (vXPermMap != null) {
                    XXResource xXResource = xXResourceDao.getById(vXPermMap.getResourceId());

                    if (xXResource != null) {
                        logger.warn("Deleting '{}' permission from policy ID='{}' for group '{}'", AppConstants.getLabelFor_XAPermType(vXPermMap.getPermType()), vXPermMap.getResourceId(), vXPermMap.getGroupName());
                    }

                    xXPermMapDao.remove(vXPermMap.getId());
                }
            }

            //delete XXAuditMap records of matching group
            XXAuditMapDao xXAuditMapDao = daoManager.getXXAuditMap();

            for (VXAuditMap vXAuditMap : vXAuditMapList.getList()) {
                if (vXAuditMap != null) {
                    xXResourceDao.getById(vXAuditMap.getResourceId());

                    xXAuditMapDao.remove(vXAuditMap.getId());
                }
            }

            //delete XXPolicyItemGroupPerm records of group
            for (XXPolicy xXPolicy : xXPolicyList) {
                RangerPolicy           rangerPolicy = policyService.getPopulatedViewObject(xXPolicy);
                List<RangerPolicyItem> policyItems  = rangerPolicy.getPolicyItems();

                removeUserGroupReferences(policyItems, null, vXGroup.getName());
                rangerPolicy.setPolicyItems(policyItems);

                List<RangerPolicyItem> denyPolicyItems = rangerPolicy.getDenyPolicyItems();
                removeUserGroupReferences(denyPolicyItems, null, vXGroup.getName());
                rangerPolicy.setDenyPolicyItems(denyPolicyItems);

                List<RangerPolicyItem> allowExceptions = rangerPolicy.getAllowExceptions();
                removeUserGroupReferences(allowExceptions, null, vXGroup.getName());
                rangerPolicy.setAllowExceptions(allowExceptions);

                List<RangerPolicyItem> denyExceptions = rangerPolicy.getDenyExceptions();
                removeUserGroupReferences(denyExceptions, null, vXGroup.getName());
                rangerPolicy.setDenyExceptions(denyExceptions);

                List<RangerDataMaskPolicyItem> dataMaskItems = rangerPolicy.getDataMaskPolicyItems();
                removeUserGroupReferences(dataMaskItems, null, vXGroup.getName());
                rangerPolicy.setDataMaskPolicyItems(dataMaskItems);

                List<RangerRowFilterPolicyItem> rowFilterItems = rangerPolicy.getRowFilterPolicyItems();
                removeUserGroupReferences(rowFilterItems, null, vXGroup.getName());
                rangerPolicy.setRowFilterPolicyItems(rowFilterItems);

                try {
                    if (StringUtils.equals(rangerPolicy.getServiceType(), EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_GDS_NAME)) {
                        Map<String, RangerPolicyResource> resources = rangerPolicy.getResources();

                        if (MapUtils.isEmpty(resources)) {
                            continue;
                        }

                        if (resources.containsKey(GdsDBStore.RESOURCE_NAME_DATASET_ID)) {
                            RangerPolicyResource policyRes = resources.get(GdsDBStore.RESOURCE_NAME_DATASET_ID);
                            List<String>         resValues = policyRes != null ? policyRes.getValues() : null;

                            if (CollectionUtils.isNotEmpty(resValues)) {
                                gdsStore.updateDatasetPolicy(Long.valueOf(resValues.get(0)), rangerPolicy);
                            }
                        } else if (resources.containsKey(GdsDBStore.RESOURCE_NAME_PROJECT_ID)) {
                            RangerPolicyResource policyRes = resources.get(GdsDBStore.RESOURCE_NAME_PROJECT_ID);
                            List<String>         resValues = policyRes != null ? policyRes.getValues() : null;

                            if (CollectionUtils.isNotEmpty(resValues)) {
                                gdsStore.updateProjectPolicy(Long.valueOf(resValues.get(0)), rangerPolicy);
                            }
                        }
                    } else {
                        svcStore.updatePolicy(rangerPolicy);
                    }
                } catch (Throwable excp) {
                    logger.error("updatePolicy({}) failed", rangerPolicy, excp);

                    throw restErrorUtil.createRESTException(excp.getMessage());
                }
            }

            if (CollectionUtils.isNotEmpty(xXGroupPermissions)) {
                for (XXGroupPermission xXGroupPermission : xXGroupPermissions) {
                    if (xXGroupPermission != null) {
                        XXModuleDef xXModuleDef = daoManager.getXXModuleDef().findByModuleId(xXGroupPermission.getModuleId());

                        if (xXModuleDef != null) {
                            logger.warn("Deleting '{}' module permission for group '{}'", xXModuleDef.getModule(), xXGroup.getName());
                        }

                        xXGroupPermissionDao.remove(xXGroupPermission.getId());
                    }
                }
            }

            //delete group from audit filter configs
            svcStore.updateServiceAuditConfig(vXGroup.getName(), REMOVE_REF_TYPE.GROUP);

            // delete group from dataset,datashare,project
            gdsStore.deletePrincipalFromGdsAcl(REMOVE_REF_TYPE.GROUP.toString(), vXGroup.getName());

            //delete XXGroup
            xXGroupDao.remove(id);

            //Create XXTrxLog
            xGroupService.createTransactionLog(xGroupService.populateViewBean(xXGroup), null, OPERATION_DELETE_CONTEXT);
        } else {
            boolean hasReferences = vxGroupUserList.getListSize() > 0;

            if (!hasReferences && CollectionUtils.isNotEmpty(xXPolicyList)) {
                hasReferences = true;
            }

            if (!hasReferences && vXPermMapList.getListSize() > 0) {
                hasReferences = true;
            }

            if (!hasReferences && vXAuditMapList.getListSize() > 0) {
                hasReferences = true;
            }

            if (!hasReferences && CollectionUtils.isNotEmpty(xXGroupPermissions)) {
                hasReferences = true;
            }

            if (hasReferences) { //change visibility to Hidden
                if (vXGroup.getIsVisible() == RangerCommonEnums.IS_VISIBLE) {
                    vXGroup.setIsVisible(RangerCommonEnums.IS_HIDDEN);

                    xGroupService.updateResource(vXGroup);
                }
            } else {
                //delete XXGroup
                xXGroupDao.remove(id);

                //Create XXTrxLog
                xGroupService.createTransactionLog(xGroupService.populateViewBean(xXGroup), null, OPERATION_DELETE_CONTEXT);
            }
        }
    }

    @Override
    public VXGroupList searchXGroups(SearchCriteria searchCriteria) {
        VXGroupList vXGroupList       = new VXGroupList();
        VXGroup     vXGroupExactMatch = null;
        VXUser      loggedInVXUser    = null;

        try {
            //In case of user we need to fetch only its associated groups.
            UserSessionBase userSession = ContextUtil.getCurrentUserSession();

            if (userSession != null && userSession.getUserRoleList().size() == 1 && userSession.getUserRoleList().contains(RangerConstants.ROLE_USER) && userSession.getLoginId() != null) {
                loggedInVXUser = xUserService.getXUserByUserName(userSession.getLoginId());

                if (loggedInVXUser != null) {
                    searchCriteria.addParam("userId", loggedInVXUser.getId());
                }
            }

            VXGroupList vXGroupListSort = new VXGroupList();

            if (searchCriteria.getParamList() != null && searchCriteria.getParamList().get("name") != null) {
                searchCriteria.setSortBy("name");

                vXGroupListSort   = xGroupService.searchXGroups(searchCriteria);
                vXGroupExactMatch = getGroupByGroupName((String) searchCriteria.getParamList().get("name"));
            }

            int vXGroupExactMatchwithSearchCriteria = 0;

            if (vXGroupExactMatch != null) {
                HashMap<String, Object> searchCriteriaParamList = searchCriteria.getParamList();

                vXGroupExactMatchwithSearchCriteria = 1;

                for (Map.Entry<String, Object> entry : searchCriteriaParamList.entrySet()) {
                    String caseKey = entry.getKey();

                    switch (caseKey.toLowerCase()) {
                        case "isvisible":
                            Integer isVisible = vXGroupExactMatch.getIsVisible();

                            if (isVisible != null && !isVisible.equals(entry.getValue())) {
                                vXGroupExactMatchwithSearchCriteria = -1;
                            }
                            break;
                        case "groupsource":
                            Integer groupsource = vXGroupExactMatch.getGroupSource();

                            if (!groupsource.equals(entry.getValue())) {
                                vXGroupExactMatchwithSearchCriteria = -1;
                            }
                            break;
                        //Its required because we need to filter groups for user role
                        case "userid":
                            if (loggedInVXUser != null) {
                                List<Long> listGroupId = daoManager.getXXGroupUser().findGroupIdListByUserId(loggedInVXUser.getId());

                                if (!listGroupId.contains(vXGroupExactMatch.getId())) {
                                    vXGroupExactMatchwithSearchCriteria = -1;
                                }
                            }

                            break;
                        default:
                            logger.warn("XUserMgr.searchXGroups: unexpected searchCriteriaParam:{}", caseKey);
                            break;
                    }

                    if (vXGroupExactMatchwithSearchCriteria == -1) {
                        break;
                    }
                }
            }

            if (vXGroupExactMatchwithSearchCriteria == 1) {
                List<VXGroup> vXGroups = new ArrayList<>();

                if (searchCriteria.getStartIndex() == 0) {
                    vXGroups.add(0, vXGroupExactMatch);
                }

                for (VXGroup vXGroup : vXGroupListSort.getList()) {
                    if (vXGroupExactMatch.getId() != null && vXGroup != null) {
                        if (!vXGroupExactMatch.getId().equals(vXGroup.getId())) {
                            vXGroups.add(vXGroup);
                        }
                    }
                }

                vXGroupList.setVXGroups(vXGroups);
                vXGroupList.setStartIndex(searchCriteria.getStartIndex());
                vXGroupList.setResultSize(vXGroupList.getList().size());
                vXGroupList.setTotalCount(vXGroupListSort.getTotalCount());
                vXGroupList.setPageSize(searchCriteria.getMaxRows());
                vXGroupList.setSortBy(searchCriteria.getSortBy());
                vXGroupList.setSortType(searchCriteria.getSortType());
            }
        } catch (Exception e) {
            logger.error("Error getting the exact match of group =>{}", String.valueOf(e));
        }

        if (vXGroupList.getList().isEmpty()) {
            if (StringUtils.isBlank(searchCriteria.getSortBy())) {
                searchCriteria.setSortBy("id");
            }

            vXGroupList = xGroupService.searchXGroups(searchCriteria);
        }

        if (vXGroupList != null && !hasAccessToModule(RangerConstants.MODULE_USER_GROUPS)) {
            if (vXGroupList.getListSize() > 0) {
                List<VXGroup> listMasked = new ArrayList<>();

                for (VXGroup vXGroup : vXGroupList.getList()) {
                    vXGroup = getMaskedVXGroup(vXGroup);

                    listMasked.add(vXGroup);
                }

                vXGroupList.setVXGroups(listMasked);
            }
        }

        return vXGroupList;
    }

    public VXUser getXUser(Long id) {
        VXUser vXUser = xUserService.readResourceWithOutLogin(id);

        if (vXUser != null && !hasAccessToGetUserInfo(vXUser)) {
            logger.info(MSG_DATA_ACCESS_DENY);

            throw restErrorUtil.createRESTException(HttpServletResponse.SC_FORBIDDEN, MSG_DATA_ACCESS_DENY, true);
        }

        if (vXUser != null && !hasAccessToModule(RangerConstants.MODULE_USER_GROUPS)) {
            vXUser = getMaskedVXUser(vXUser);
        }

        return vXUser;
    }

    public VXUser createXUser(VXUser vXUser) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        if (vXUser.getUserSource() == RangerCommonEnums.USER_FEDERATED) {
            if (StringUtils.isEmpty(vXUser.getPassword())) {
                PasswordGenerator passwordGenerator = new PasswordGenerator.PasswordGeneratorBuilder()
                        .useLower(true)
                        .useUpper(true)
                        .useDigits(true)
                        .useSymbols(true)
                        .build();
                String passWd = passwordGenerator.generate(PASSWORD_LENGTH);

                vXUser.setPassword(passWd);
            }
        }

        validatePassword(vXUser);

        String userName  = vXUser.getName();
        String firstName = vXUser.getFirstName();

        if (userName == null || "null".equalsIgnoreCase(userName) || userName.trim().isEmpty()) {
            throw restErrorUtil.createRESTException("Please provide a valid username.", MessageEnums.INVALID_INPUT_DATA);
        }

        if (firstName == null || "null".equalsIgnoreCase(firstName) || firstName.trim().isEmpty()) {
            throw restErrorUtil.createRESTException("Please provide a valid first name.", MessageEnums.INVALID_INPUT_DATA);
        }

        if (vXUser.getDescription() == null) {
            vXUser.setDescription(vXUser.getName());
        }

        String       actualPassword = vXUser.getPassword();
        VXPortalUser vXPortalUser   = new VXPortalUser();

        vXPortalUser.setLoginId(userName);
        vXPortalUser.setFirstName(vXUser.getFirstName());

        if ("null".equalsIgnoreCase(vXPortalUser.getFirstName())) {
            vXPortalUser.setFirstName("");
        }

        vXPortalUser.setLastName(vXUser.getLastName());

        if ("null".equalsIgnoreCase(vXPortalUser.getLastName())) {
            vXPortalUser.setLastName("");
        }

        String emailAddress = vXUser.getEmailAddress();

        if (StringUtils.isNotEmpty(emailAddress) && !stringUtil.validateEmail(emailAddress)) {
            logger.warn("Invalid email address:{}", emailAddress);

            throw restErrorUtil.createRESTException("Please provide valid email address.", MessageEnums.INVALID_INPUT_DATA);
        }

        vXPortalUser.setEmailAddress(emailAddress);

        if (vXPortalUser.getFirstName() != null && vXPortalUser.getLastName() != null && !vXPortalUser.getFirstName().trim().isEmpty() && !vXPortalUser.getLastName().trim().isEmpty()) {
            vXPortalUser.setPublicScreenName(vXPortalUser.getFirstName() + " " + vXPortalUser.getLastName());
        } else {
            vXPortalUser.setPublicScreenName(vXUser.getName());
        }

        vXPortalUser.setPassword(actualPassword);
        vXPortalUser.setUserRoleList(vXUser.getUserRoleList());
        vXPortalUser.setUserSource(vXUser.getUserSource());

        vXPortalUser = userMgr.createDefaultAccountUser(vXPortalUser);

        VXUser createdXUser = xUserService.createResource(vXUser);

        createdXUser.setPassword(actualPassword);

        List<XXTrxLogV2> trxLogList     = xUserService.getTransactionLog(createdXUser, null, OPERATION_CREATE_CONTEXT);
        String           hiddenPassword = PropertiesUtil.getProperty("ranger.password.hidden", "*****");

        createdXUser.setPassword(hiddenPassword);

        Collection<String> groupNamesList = new ArrayList<>();
        Collection<Long>   groupIdList    = vXUser.getGroupIdList();
        List<VXGroupUser>  vXGroupUsers   = new ArrayList<>();

        if (groupIdList != null) {
            for (Long groupId : groupIdList) {
                VXGroupUser vXGroupUser = createXGroupUser(createdXUser.getId(), groupId);

                // trxLogList.addAll(xGroupUserService.getTransactionLog(
                // vXGroupUser, "create"));
                vXGroupUsers.add(vXGroupUser);

                groupNamesList.add(vXGroupUser.getName());
            }
        }

        createdXUser.setGroupIdList(groupIdList);
        createdXUser.setGroupNameList(groupNamesList);

        for (VXGroupUser vXGroupUser : vXGroupUsers) {
            List<XXTrxLogV2> groupUserTrxLogs = xGroupUserService.getTransactionLog(vXGroupUser, null, OPERATION_CREATE_CONTEXT);

            if (CollectionUtils.isNotEmpty(groupUserTrxLogs)) {
                if (trxLogList == null) {
                    trxLogList = new ArrayList<>();
                }

                trxLogList.addAll(groupUserTrxLogs);
            }
        }

        //
        xaBizUtil.createTrxLog(trxLogList);

        if (vXPortalUser != null) {
            assignPermissionToUser(vXPortalUser, true);
        }

        updateUserStoreVersion("createXUser(" + vXUser.getName() + ")");

        return createdXUser;
    }

    public VXUser updateXUser(VXUser vXUser) {
        if (vXUser == null || vXUser.getName() == null || "null".equalsIgnoreCase(vXUser.getName()) || vXUser.getName().trim().isEmpty()) {
            throw restErrorUtil.createRESTException("Please provide a valid " + "username.", MessageEnums.INVALID_INPUT_DATA);
        }

        String firstName = vXUser.getFirstName();

        if (firstName == null || "null".equalsIgnoreCase(firstName) || firstName.trim().isEmpty()) {
            throw restErrorUtil.createRESTException("Please provide a valid first name.", MessageEnums.INVALID_INPUT_DATA);
        }

        checkAccess(vXUser);

        xaBizUtil.blockAuditorRoleUser();

        VXPortalUser oldUserProfile = userMgr.getUserProfileByLoginId(vXUser.getName());

        if (oldUserProfile == null) {
            throw restErrorUtil.createRESTException("user " + vXUser.getName() + " does not exist.", MessageEnums.INVALID_INPUT_DATA);
        }

        VXPortalUser vXPortalUser = new VXPortalUser();

        if (oldUserProfile.getId() != null) {
            vXPortalUser.setId(oldUserProfile.getId());
        }

        vXPortalUser.setFirstName(vXUser.getFirstName());

        if ("null".equalsIgnoreCase(vXPortalUser.getFirstName())) {
            vXPortalUser.setFirstName("");
        }

        vXPortalUser.setLastName(vXUser.getLastName());

        if ("null".equalsIgnoreCase(vXPortalUser.getLastName())) {
            vXPortalUser.setLastName("");
        }

        vXPortalUser.setEmailAddress(vXUser.getEmailAddress());
        vXPortalUser.setLoginId(vXUser.getName());
        vXPortalUser.setStatus(vXUser.getStatus());
        vXPortalUser.setUserRoleList(vXUser.getUserRoleList());

        if (vXPortalUser.getFirstName() != null && vXPortalUser.getLastName() != null && !vXPortalUser.getFirstName().trim().isEmpty() && !vXPortalUser.getLastName().trim().isEmpty()) {
            vXPortalUser.setPublicScreenName(vXPortalUser.getFirstName() + " " + vXPortalUser.getLastName());
        } else {
            vXPortalUser.setPublicScreenName(vXUser.getName());
        }

        vXPortalUser.setUserSource(oldUserProfile.getUserSource());

        String hiddenPasswordString = PropertiesUtil.getProperty("ranger.password.hidden", "*****");
        String password             = vXUser.getPassword();

        if (password != null && password.equals(hiddenPasswordString)) {
            vXPortalUser.setPassword(oldUserProfile.getPassword());
        } else if (oldUserProfile.getUserSource() == RangerCommonEnums.USER_EXTERNAL && password != null) {
            vXPortalUser.setPassword(oldUserProfile.getPassword());

            logger.debug("User is trrying to change external user password which we are not allowing it to change");
        } else if (password != null) {
            validatePassword(vXUser);

            vXPortalUser.setPassword(password);
        }

        Collection<Long> groupIdList  = vXUser.getGroupIdList();
        VXUser           existing     = (vXUser.getId() != null) ? xUserService.readResource(vXUser.getId()) : null;
        XXPortalUser     xXPortalUser = userMgr.updateUserWithPass(vXPortalUser);

        //update permissions start
        Collection<String> roleListUpdatedProfile = new ArrayList<>();

        if (oldUserProfile.getId() != null) {
            if (vXUser.getUserRoleList() != null) {
                Collection<String> roleListOldProfile = oldUserProfile.getUserRoleList();
                Collection<String> roleListNewProfile = vXUser.getUserRoleList();

                if (roleListNewProfile != null && roleListOldProfile != null) {
                    for (String role : roleListNewProfile) {
                        if (role != null && !roleListOldProfile.contains(role)) {
                            roleListUpdatedProfile.add(role);
                        }
                    }
                }
            }
        }

        if (!roleListUpdatedProfile.isEmpty()) {
            vXPortalUser.setUserRoleList(roleListUpdatedProfile);

            List<XXUserPermission> xuserPermissionList = daoManager.getXXUserPermission().findByUserPermissionId(vXPortalUser.getId());

            if (xuserPermissionList != null && !xuserPermissionList.isEmpty()) {
                for (XXUserPermission xXUserPermission : xuserPermissionList) {
                    if (xXUserPermission != null) {
                        try {
                            xUserPermissionService.deleteResource(xXUserPermission.getId());
                        } catch (Exception e) {
                            logger.error(e.getMessage());
                        }
                    }
                }
            }

            assignPermissionToUser(vXPortalUser, true);
        }

        //update permissions end
        Collection<String> roleList = new ArrayList<>();

        if (xXPortalUser != null) {
            roleList = userMgr.getRolesForUser(xXPortalUser);
        }

        if (roleList == null || roleList.isEmpty()) {
            roleList = new ArrayList<>();

            roleList.add(RangerConstants.ROLE_USER);
        }

        // TODO I've to get the transaction log from here.
        // There is nothing to log anything in XXUser so far.
        vXUser = xUserService.updateResource(vXUser);

        vXUser.setUserRoleList(roleList);

        if (oldUserProfile.getUserSource() == RangerCommonEnums.USER_APP) {
            vXUser.setPassword(password);
        } else if (oldUserProfile.getUserSource() == RangerCommonEnums.USER_EXTERNAL) {
            vXUser.setPassword(oldUserProfile.getPassword());
        }

        if (password == null) {
            vXUser.setPassword(hiddenPasswordString); //To stop Auditing Password transaction log, when it is not edited.
        }

        List<XXTrxLogV2> trxLogList = xUserService.getTransactionLog(vXUser, existing, OPERATION_UPDATE_CONTEXT);

        vXUser.setPassword(hiddenPasswordString);

        Long             userId             = vXUser.getId();
        List<Long>       groupUsersToRemove = new ArrayList<>();
        List<XXTrxLogV2> groupUserTrxLogs   = createOrDelGrpUserWithUpdatedGrpId(vXUser, groupIdList, userId, groupUsersToRemove);

        if (CollectionUtils.isNotEmpty(groupUserTrxLogs)) {
            if (trxLogList == null) {
                trxLogList = new ArrayList<>();
            }

            trxLogList.addAll(groupUserTrxLogs);
        }

        xaBizUtil.createTrxLog(trxLogList);

        updateUserStoreVersion("updateXUser(" + vXUser.getName() + ")");

        return vXUser;
    }

    public synchronized void deleteXUser(Long id, boolean force) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        XXUserDao xXUserDao = daoManager.getXXUser();
        XXUser    xXUser    = xXUserDao.getById(id);

        if (xXUser == null) {
            throw restErrorUtil.create404RESTException("Data Not Found for given Id", MessageEnums.DATA_NOT_FOUND, id, null, "readResource : No Object found with given id.");
        }

        VXUser vXUser = xUserService.populateViewBean(xXUser);

        if (vXUser == null || StringUtils.isEmpty(vXUser.getName())) {
            throw restErrorUtil.createRESTException("No user found with id=" + id);
        }

        XXPortalUserDao xXPortalUserDao = daoManager.getXXPortalUser();
        XXPortalUser    xXPortalUser    = xXPortalUserDao.findByLoginId(vXUser.getName().trim());
        VXPortalUser    vXPortalUser    = null;

        if (xXPortalUser != null) {
            vXPortalUser = xPortalUserService.populateViewBean(xXPortalUser);
        }

        if (vXPortalUser == null || StringUtils.isEmpty(vXPortalUser.getLoginId())) {
            throw restErrorUtil.createRESTException("No user found with id=" + id);
        }

        logger.debug("Force delete status={} for user={}", force, vXUser.getName());

        restrictSelfAccountDeletion(vXUser.getName().trim());

        blockIfZoneUser(id);

        this.blockIfRoleUser(id);

        SearchCriteria searchCriteria = new SearchCriteria();

        searchCriteria.addParam("xUserId", id);

        VXGroupUserList vxGroupUserList = searchXGroupUsers(searchCriteria);

        searchCriteria = new SearchCriteria();

        searchCriteria.addParam("userId", id);

        VXPermMapList vXPermMapList = searchXPermMaps(searchCriteria);

        searchCriteria = new SearchCriteria();

        searchCriteria.addParam("userId", id);

        VXAuditMapList         vXAuditMapList      = searchXAuditMaps(searchCriteria);
        long                   xXPortalUserId      = vXPortalUser.getId();
        XXAuthSessionDao       xXAuthSessionDao    = daoManager.getXXAuthSession();
        XXUserPermissionDao    xXUserPermissionDao = daoManager.getXXUserPermission();
        XXPortalUserRoleDao    xXPortalUserRoleDao = daoManager.getXXPortalUserRole();
        List<Long>             xXAuthSessionIds    = xXAuthSessionDao.getAuthSessionIdsByUserId(xXPortalUserId);
        List<XXUserPermission> xXUserPermissions   = xXUserPermissionDao.findByUserPermissionId(xXPortalUserId);
        List<XXPortalUserRole> xXPortalUserRoles   = xXPortalUserRoleDao.findByUserId(xXPortalUserId);
        XXPolicyDao            xXPolicyDao         = daoManager.getXXPolicy();

        logger.warn("Deleting User : {}", vXUser.getName());

        if (force) {
            //delete XXGroupUser mapping
            XXGroupUserDao xGroupUserDao = daoManager.getXXGroupUser();

            for (VXGroupUser groupUser : vxGroupUserList.getList()) {
                if (groupUser != null) {
                    logger.warn("Removing user '{}' from group '{}'", vXUser.getName(), groupUser.getName());

                    xGroupUserDao.remove(groupUser.getId());
                }
            }

            //delete XXPermMap records of user
            XXPermMapDao xXPermMapDao = daoManager.getXXPermMap();

            for (VXPermMap vXPermMap : vXPermMapList.getList()) {
                if (vXPermMap != null) {
                    logger.warn("Deleting '{}' permission from policy ID='{}' for user '{}'", AppConstants.getLabelFor_XAPermType(vXPermMap.getPermType()), vXPermMap.getResourceId(), vXPermMap.getUserName());

                    xXPermMapDao.remove(vXPermMap.getId());
                }
            }

            //delete XXAuditMap records of user
            XXAuditMapDao xXAuditMapDao = daoManager.getXXAuditMap();

            for (VXAuditMap vXAuditMap : vXAuditMapList.getList()) {
                if (vXAuditMap != null) {
                    xXAuditMapDao.remove(vXAuditMap.getId());
                }
            }

            //delete XXPortalUser references
            xPortalUserService.updateXXPortalUserReferences(xXPortalUserId);

            if (CollectionUtils.isNotEmpty(xXAuthSessionIds)) {
                logger.warn("Deleting {} login session records for user '{}'", xXAuthSessionIds.size(), vXPortalUser.getLoginId());

                xXAuthSessionDao.deleteAuthSessionsByIds(xXAuthSessionIds);
            }

            for (XXUserPermission xXUserPermission : xXUserPermissions) {
                if (xXUserPermission != null) {
                    XXModuleDef xXModuleDef = daoManager.getXXModuleDef().findByModuleId(xXUserPermission.getModuleId());

                    if (xXModuleDef != null) {
                        logger.warn("Deleting '{}' module permission for user '{}'", xXModuleDef.getModule(), vXPortalUser.getLoginId());
                    }

                    xXUserPermissionDao.remove(xXUserPermission.getId());
                }
            }

            for (XXPortalUserRole xXPortalUserRole : xXPortalUserRoles) {
                if (xXPortalUserRole != null) {
                    logger.warn("Deleting '{}' role for user '{}'", xXPortalUserRole.getUserRole(), vXPortalUser.getLoginId());

                    xXPortalUserRoleDao.remove(xXPortalUserRole.getId());
                }
            }

            //delete XXPolicyItemUserPerm records of user
            List<XXPolicy> xXPolicyList = xXPolicyDao.findByUserId(id);

            for (XXPolicy xXPolicy : xXPolicyList) {
                RangerPolicy           rangerPolicy = policyService.getPopulatedViewObject(xXPolicy);
                List<RangerPolicyItem> policyItems  = rangerPolicy.getPolicyItems();

                removeUserGroupReferences(policyItems, vXUser.getName(), null);
                rangerPolicy.setPolicyItems(policyItems);

                List<RangerPolicyItem> denyPolicyItems = rangerPolicy.getDenyPolicyItems();

                removeUserGroupReferences(denyPolicyItems, vXUser.getName(), null);
                rangerPolicy.setDenyPolicyItems(denyPolicyItems);

                List<RangerPolicyItem> allowExceptions = rangerPolicy.getAllowExceptions();

                removeUserGroupReferences(allowExceptions, vXUser.getName(), null);
                rangerPolicy.setAllowExceptions(allowExceptions);

                List<RangerPolicyItem> denyExceptions = rangerPolicy.getDenyExceptions();

                removeUserGroupReferences(denyExceptions, vXUser.getName(), null);
                rangerPolicy.setDenyExceptions(denyExceptions);

                List<RangerDataMaskPolicyItem> dataMaskItems = rangerPolicy.getDataMaskPolicyItems();

                removeUserGroupReferences(dataMaskItems, vXUser.getName(), null);
                rangerPolicy.setDataMaskPolicyItems(dataMaskItems);

                List<RangerRowFilterPolicyItem> rowFilterItems = rangerPolicy.getRowFilterPolicyItems();

                removeUserGroupReferences(rowFilterItems, vXUser.getName(), null);
                rangerPolicy.setRowFilterPolicyItems(rowFilterItems);

                try {
                    if (StringUtils.equals(rangerPolicy.getServiceType(), EmbeddedServiceDefsUtil.EMBEDDED_SERVICEDEF_GDS_NAME)) {
                        Map<String, RangerPolicyResource> resources = rangerPolicy.getResources();

                        if (MapUtils.isEmpty(resources)) {
                            continue;
                        }

                        if (resources.containsKey(GdsDBStore.RESOURCE_NAME_DATASET_ID)) {
                            RangerPolicyResource policyRes = resources.get(GdsDBStore.RESOURCE_NAME_DATASET_ID);
                            List<String>         resValues = policyRes != null ? policyRes.getValues() : null;

                            if (CollectionUtils.isNotEmpty(resValues)) {
                                gdsStore.updateDatasetPolicy(Long.valueOf(resValues.get(0)), rangerPolicy);
                            }
                        } else if (resources.containsKey(GdsDBStore.RESOURCE_NAME_PROJECT_ID)) {
                            RangerPolicyResource policyRes = resources.get(GdsDBStore.RESOURCE_NAME_PROJECT_ID);
                            List<String>         resValues = policyRes != null ? policyRes.getValues() : null;

                            if (CollectionUtils.isNotEmpty(resValues)) {
                                gdsStore.updateProjectPolicy(Long.valueOf(resValues.get(0)), rangerPolicy);
                            }
                        }
                    } else {
                        svcStore.updatePolicy(rangerPolicy);
                    }
                } catch (Throwable excp) {
                    logger.error("updatePolicy({}) failed", rangerPolicy, excp);

                    throw restErrorUtil.createRESTException(excp.getMessage());
                }
            }

            //delete user from audit filter configs
            svcStore.updateServiceAuditConfig(vXUser.getName(), REMOVE_REF_TYPE.USER);

            //delete gdsObject mapping of user
            gdsStore.deletePrincipalFromGdsAcl(REMOVE_REF_TYPE.USER.toString(), vXUser.getName());

            //delete XXUser entry of user
            xXUserDao.remove(id);

            //delete XXPortal entry of user
            logger.warn("Deleting Portal User : {}", vXPortalUser.getLoginId());

            xXPortalUserDao.remove(xXPortalUserId);

            xUserService.createTransactionLog(xUserService.populateViewBean(xXUser), null, OPERATION_DELETE_CONTEXT);

            xPortalUserService.createTransactionLog(xPortalUserService.populateViewBean(xXPortalUser), null, OPERATION_DELETE_CONTEXT);
        } else {
            boolean        hasReferences = false;
            List<XXPolicy> xXPolicyList  = xXPolicyDao.findByUserId(id);

            if (vxGroupUserList != null && vxGroupUserList.getListSize() > 0) {
                hasReferences = true;
            }

            if (!hasReferences && xXPolicyList != null && !xXPolicyList.isEmpty()) {
                hasReferences = true;
            }

            if (!hasReferences && vXPermMapList != null && vXPermMapList.getListSize() > 0) {
                hasReferences = true;
            }

            if (!hasReferences && vXAuditMapList != null && vXAuditMapList.getListSize() > 0) {
                hasReferences = true;
            }

            if (!hasReferences && CollectionUtils.isNotEmpty(xXAuthSessionIds)) {
                hasReferences = true;
            }

            if (!hasReferences && xXUserPermissions != null && !xXUserPermissions.isEmpty()) {
                hasReferences = true;
            }

            if (!hasReferences && xXPortalUserRoles != null && !xXPortalUserRoles.isEmpty()) {
                hasReferences = true;
            }

            if (hasReferences) {
                if (vXUser.getIsVisible() != RangerCommonEnums.IS_HIDDEN) {
                    logger.info("Updating visibility of user '{}' to Hidden!", vXUser.getName());

                    vXUser.setIsVisible(RangerCommonEnums.IS_HIDDEN);

                    xUserService.updateResource(vXUser);
                }
            } else {
                xPortalUserService.updateXXPortalUserReferences(xXPortalUserId);

                //delete XXUser entry of user
                xXUserDao.remove(id);

                //delete XXPortal entry of user
                logger.warn("Deleting Portal User : {}", vXPortalUser.getLoginId());

                xXPortalUserDao.remove(xXPortalUserId);

                xUserService.createTransactionLog(xUserService.populateViewBean(xXUser), null, OPERATION_DELETE_CONTEXT);

                xPortalUserService.createTransactionLog(xPortalUserService.populateViewBean(xXPortalUser), null, OPERATION_DELETE_CONTEXT);
            }
        }
    }

    @Override
    public VXUserList searchXUsers(SearchCriteria searchCriteria) {
        VXUserList vXUserList       = new VXUserList();
        VXUser     vXUserExactMatch = null;

        try {
            VXUserList vXUserListSort = new VXUserList();

            if (searchCriteria.getParamList() != null && searchCriteria.getParamList().get("name") != null) {
                searchCriteria.setSortBy("name");

                vXUserListSort   = xUserService.searchXUsers(searchCriteria);
                vXUserExactMatch = getXUserByUserName((String) searchCriteria.getParamList().get("name"));
            }

            int vXUserExactMatchwithSearchCriteria = 0;

            if (vXUserExactMatch != null) {
                vXUserListSort = xUserService.searchXUsers(searchCriteria);

                HashMap<String, Object> searchCriteriaParamList = searchCriteria.getParamList();

                vXUserExactMatchwithSearchCriteria = 1;

                for (Map.Entry<String, Object> entry : searchCriteriaParamList.entrySet()) {
                    String caseKey = entry.getKey();

                    switch (caseKey.toLowerCase()) {
                        case "isvisible":
                            Integer isVisible = vXUserExactMatch.getIsVisible();

                            if (isVisible != null && !isVisible.equals(entry.getValue())) {
                                vXUserExactMatchwithSearchCriteria = -1;
                            }
                            break;
                        case "status":
                            Integer status = vXUserExactMatch.getStatus();

                            if (!status.equals(entry.getValue())) {
                                vXUserExactMatchwithSearchCriteria = -1;
                            }
                            break;
                        case "usersource":
                            Integer userSource = vXUserExactMatch.getUserSource();

                            if (!userSource.equals(entry.getValue())) {
                                vXUserExactMatchwithSearchCriteria = -1;
                            }
                            break;
                        case "emailaddress":
                            String email = (String) entry.getValue();

                            if (email != null && !email.equals(vXUserExactMatch.getEmailAddress())) {
                                vXUserExactMatchwithSearchCriteria = -1;
                            }
                            break;
                        case "userrole":
                            if (vXUserExactMatch.getUserRoleList() != null && !vXUserExactMatch.getUserRoleList().contains(entry.getValue())) {
                                vXUserExactMatchwithSearchCriteria = -1;
                            }
                            break;
                        case "userrolelist":
                            @SuppressWarnings("unchecked")
                            Collection<String> userrolelist = (Collection<String>) entry.getValue();

                            if (!CollectionUtils.isEmpty(userrolelist)) {
                                for (String role : userrolelist) {
                                    if (vXUserExactMatch.getUserRoleList() != null && vXUserExactMatch.getUserRoleList().contains(role)) {
                                        vXUserExactMatchwithSearchCriteria = 1;
                                        break;
                                    } else {
                                        vXUserExactMatchwithSearchCriteria = -1;
                                    }
                                }
                            }
                            break;
                        default:
                            logger.warn("XUserMgr.searchXUsers: unexpected searchCriteriaParam:{}", caseKey);
                            break;
                    }

                    if (vXUserExactMatchwithSearchCriteria == -1) {
                        break;
                    }
                }
            }

            if (vXUserExactMatchwithSearchCriteria == 1) {
                VXGroupList groups = getXUserGroups(vXUserExactMatch.getId());

                if (groups.getListSize() > 0) {
                    Collection<String> groupNameList = new ArrayList<>();
                    Collection<Long>   groupIdList   = new ArrayList<>();

                    for (VXGroup group : groups.getList()) {
                        groupIdList.add(group.getId());

                        groupNameList.add(group.getName());
                    }

                    vXUserExactMatch.setGroupIdList(groupIdList);
                    vXUserExactMatch.setGroupNameList(groupNameList);
                }

                List<VXUser> vXUsers = new ArrayList<>();

                if (searchCriteria.getStartIndex() == 0) {
                    vXUsers.add(0, vXUserExactMatch);
                }

                for (VXUser vxUser : vXUserListSort.getVXUsers()) {
                    if (vXUserExactMatch.getId() != null && vxUser != null) {
                        if (!vXUserExactMatch.getId().equals(vxUser.getId())) {
                            vXUsers.add(vxUser);
                        }
                    }
                }

                vXUserList.setVXUsers(vXUsers);
                vXUserList.setStartIndex(searchCriteria.getStartIndex());
                vXUserList.setResultSize(vXUserList.getVXUsers().size());
                vXUserList.setTotalCount(vXUserListSort.getTotalCount());
                vXUserList.setPageSize(searchCriteria.getMaxRows());
                vXUserList.setSortBy(searchCriteria.getSortBy());
                vXUserList.setSortType(searchCriteria.getSortType());
            }
        } catch (Exception e) {
            logger.error("Error getting the exact match of user =>{}", String.valueOf(e));
        }

        if (vXUserList.getVXUsers().isEmpty()) {
            if (StringUtils.isBlank(searchCriteria.getSortBy())) {
                searchCriteria.setSortBy("id");
            }

            vXUserList = xUserService.searchXUsers(searchCriteria);
        }

        if (vXUserList != null && !hasAccessToModule(RangerConstants.MODULE_USER_GROUPS)) {
            List<VXUser> vXUsers = new ArrayList<>();

            if (vXUserList.getListSize() > 0) {
                for (VXUser vXUser : vXUserList.getList()) {
                    vXUser = getMaskedVXUser(vXUser);

                    vXUsers.add(vXUser);
                }

                vXUserList.setVXUsers(vXUsers);
            }
        }

        return vXUserList;
    }

    public VXGroupUser getXGroupUser(Long id) {
        return xGroupUserService.readResourceWithOutLogin(id);
    }

    public VXGroupUser createXGroupUser(VXGroupUser vXGroupUser) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        vXGroupUser = xGroupUserService.createXGroupUserWithOutLogin(vXGroupUser);

        return vXGroupUser;
    }

    public VXGroupUser updateXGroupUser(VXGroupUser vXGroupUser) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        return super.updateXGroupUser(vXGroupUser);
    }

    public void deleteXGroupUser(Long id, boolean force) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        super.deleteXGroupUser(id, force);
    }

    public void deleteXPermMap(Long id, boolean force) {
        xaBizUtil.blockAuditorRoleUser();

        if (force) {
            XXPermMap xPermMap = daoManager.getXXPermMap().getById(id);

            if (xPermMap != null) {
                if (xResourceService.readResource(xPermMap.getResourceId()) == null) {
                    throw restErrorUtil.createRESTException("Invalid Input Data - No resource found with Id: " + xPermMap.getResourceId(), MessageEnums.INVALID_INPUT_DATA);
                }
            }

            xPermMapService.deleteResource(id);
        } else {
            throw restErrorUtil.createRESTException("serverMsg.modelMgrBaseDeleteModel", MessageEnums.OPER_NOT_ALLOWED_FOR_ENTITY);
        }
    }

    public VXPermMapList searchXPermMaps(SearchCriteria searchCriteria) {
        VXPermMapList   returnList;
        UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();

        if (currentUserSession != null && currentUserSession.isUserAdmin()) {
            returnList = super.searchXPermMaps(searchCriteria);
        } else {
            returnList = new VXPermMapList();

            int startIndex = searchCriteria.getStartIndex();
            int pageSize   = searchCriteria.getMaxRows();

            searchCriteria.setStartIndex(0);
            searchCriteria.setMaxRows(Integer.MAX_VALUE);

            List<VXPermMap> resultList            = xPermMapService.searchXPermMaps(searchCriteria).getVXPermMaps();
            List<VXPermMap> adminPermResourceList = new ArrayList<>();

            for (VXPermMap xXPermMap : resultList) {
                XXResource xRes       = daoManager.getXXResource().getById(xXPermMap.getResourceId());
                VXResponse vXResponse = msBizUtil.hasPermission(xResourceService.populateViewBean(xRes), AppConstants.XA_PERM_TYPE_ADMIN);

                if (vXResponse.getStatusCode() == VXResponse.STATUS_SUCCESS) {
                    adminPermResourceList.add(xXPermMap);
                }
            }

            if (!adminPermResourceList.isEmpty()) {
                populatePageList(adminPermResourceList, startIndex, pageSize, returnList);
            }
        }

        return returnList;
    }

    public VXLong getXPermMapSearchCount(SearchCriteria searchCriteria) {
        VXPermMapList permMapList = xPermMapService.searchXPermMaps(searchCriteria);
        VXLong        vXLong      = new VXLong();

        vXLong.setValue(permMapList.getListSize());

        return vXLong;
    }

    public void deleteXAuditMap(Long id, boolean force) {
        xaBizUtil.blockAuditorRoleUser();

        if (force) {
            XXAuditMap xAuditMap = daoManager.getXXAuditMap().getById(id);

            if (xAuditMap != null) {
                if (xResourceService.readResource(xAuditMap.getResourceId()) == null) {
                    throw restErrorUtil.createRESTException("Invalid Input Data - No resource found with Id: " + xAuditMap.getResourceId(), MessageEnums.INVALID_INPUT_DATA);
                }
            }

            xAuditMapService.deleteResource(id);
        } else {
            throw restErrorUtil.createRESTException("serverMsg.modelMgrBaseDeleteModel", MessageEnums.OPER_NOT_ALLOWED_FOR_ENTITY);
        }
    }

    public VXAuditMapList searchXAuditMaps(SearchCriteria searchCriteria) {
        VXAuditMapList  returnList         = new VXAuditMapList();
        UserSessionBase currentUserSession = ContextUtil.getCurrentUserSession();

        // If user is system admin
        if (currentUserSession != null && currentUserSession.isUserAdmin()) {
            returnList = super.searchXAuditMaps(searchCriteria);
        } else {
            int startIndex = searchCriteria.getStartIndex();
            int pageSize   = searchCriteria.getMaxRows();

            searchCriteria.setStartIndex(0);
            searchCriteria.setMaxRows(Integer.MAX_VALUE);

            List<VXAuditMap> resultList             = xAuditMapService.searchXAuditMaps(searchCriteria).getVXAuditMaps();
            List<VXAuditMap> adminAuditResourceList = new ArrayList<>();

            for (VXAuditMap xXAuditMap : resultList) {
                XXResource xRes       = daoManager.getXXResource().getById(xXAuditMap.getResourceId());
                VXResponse vXResponse = msBizUtil.hasPermission(xResourceService.populateViewBean(xRes), AppConstants.XA_PERM_TYPE_ADMIN);

                if (vXResponse.getStatusCode() == VXResponse.STATUS_SUCCESS) {
                    adminAuditResourceList.add(xXAuditMap);
                }
            }

            if (!adminAuditResourceList.isEmpty()) {
                populatePageList(adminAuditResourceList, startIndex, pageSize, returnList);
            }
        }

        return returnList;
    }

    public VXLong getXAuditMapSearchCount(SearchCriteria searchCriteria) {
        VXAuditMapList auditMapList = xAuditMapService.searchXAuditMaps(searchCriteria);
        VXLong         vXLong       = new VXLong();

        vXLong.setValue(auditMapList.getListSize());

        return vXLong;
    }

    /**
     * // public void createXGroupAndXUser(String groupName, String userName) {
     * <p>
     * // Long groupId; // Long userId; // XXGroup xxGroup = //
     * appDaoManager.getXXGroup().findByGroupName(groupName); // VXGroup
     * vxGroup; // if (xxGroup == null) { // vxGroup = new VXGroup(); //
     * vxGroup.setName(groupName); // vxGroup.setDescription(groupName); //
     * vxGroup.setGroupType(AppConstants.XA_GROUP_USER); //
     * vxGroup.setPriAcctId(1l); // vxGroup.setPriGrpId(1l); // vxGroup =
     * xGroupService.createResource(vxGroup); // groupId = vxGroup.getId(); // }
     * else { // groupId = xxGroup.getId(); // } // XXUser xxUser =
     * appDaoManager.getXXUser().findByUserName(userName); // VXUser vxUser; //
     * if (xxUser == null) { // vxUser = new VXUser(); //
     * vxUser.setName(userName); // vxUser.setDescription(userName); //
     * vxUser.setPriGrpId(1l); // vxUser.setPriAcctId(1l); // vxUser =
     * xUserService.createResource(vxUser); // userId = vxUser.getId(); // }
     * else { // userId = xxUser.getId(); // } // VXGroupUser vxGroupUser = new
     * VXGroupUser(); // vxGroupUser.setParentGroupId(groupId); //
     * vxGroupUser.setUserId(userId); // vxGroupUser.setName(groupName); //
     * vxGroupUser.setPriAcctId(1l); // vxGroupUser.setPriGrpId(1l); //
     * vxGroupUser = xGroupUserService.createResource(vxGroupUser);
     * <p>
     * // }
     */

    public void deleteXGroupAndXUser(String groupName, String userName) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        VXGroup        vxGroup        = xGroupService.getGroupByGroupName(groupName);
        VXUser         vxUser         = xUserService.getXUserByUserName(userName);
        SearchCriteria searchCriteria = new SearchCriteria();

        searchCriteria.addParam("xGroupId", vxGroup.getId());
        searchCriteria.addParam("xUserId", vxUser.getId());

        VXGroupUserList vxGroupUserList = xGroupUserService.searchXGroupUsers(searchCriteria);

        for (VXGroupUser vxGroupUser : vxGroupUserList.getList()) {
            daoManager.getXXGroupUser().remove(vxGroupUser.getId());
        }
    }

    public VXGroupList getXUserGroups(Long xUserId) {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.addParam("xUserId", xUserId);

        VXGroupUserList vXGroupUserList = xGroupUserService.searchXGroupUsers(searchCriteria);
        VXGroupList     vXGroupList     = new VXGroupList();
        List<VXGroup>   vXGroups        = new ArrayList<>();

        if (vXGroupUserList != null) {
            List<VXGroupUser> vXGroupUsers = vXGroupUserList.getList();
            Set<Long>         groupIdList  = new HashSet<>();

            for (VXGroupUser vXGroupUser : vXGroupUsers) {
                groupIdList.add(vXGroupUser.getParentGroupId());
            }

            for (Long groupId : groupIdList) {
                VXGroup vXGroup = xGroupService.readResource(groupId);

                vXGroups.add(vXGroup);
            }

            vXGroupList.setVXGroups(vXGroups);
        } else {
            logger.debug("No groups found for user id : {}", xUserId);
        }

        return vXGroupList;
    }

    public Set<String> getGroupsForUser(String userName) {
        Set<String> ret = new HashSet<>();

        try {
            VXUser user = getXUserByUserName(userName);

            if (user != null) {
                VXGroupList groups = getXUserGroups(user.getId());

                if (groups != null && !CollectionUtils.isEmpty(groups.getList())) {
                    for (VXGroup group : groups.getList()) {
                        ret.add(group.getName());
                    }
                } else {
                    logger.debug("getGroupsForUser('{}'): no groups found for user", userName);
                }
            } else {
                logger.debug("getGroupsForUser('{}'): user not found", userName);
            }
        } catch (Exception excp) {
            logger.error("getGroupsForUser('{}') failed", userName, excp);
        }

        return ret;
    }

    public VXUserList getXGroupUsers(SearchCriteria searchCriteria) {
        if (!msBizUtil.hasModuleAccess(RangerConstants.MODULE_USER_GROUPS)) {
            throw restErrorUtil.createRESTException(HttpServletResponse.SC_FORBIDDEN, "User is not having permissions on the " + RangerConstants.MODULE_USER_GROUPS + " module.", true);
        }

        VXUserList      vXUserList      = new VXUserList();
        VXGroupUserList vXGroupUserList = xGroupUserService.searchXGroupUsers(searchCriteria);
        List<VXUser>    vXUsers         = new ArrayList<>();

        if (vXGroupUserList != null) {
            List<VXGroupUser> vXGroupUsers = vXGroupUserList.getList();
            Set<Long>         userIdList   = new HashSet<>();

            for (VXGroupUser vXGroupUser : vXGroupUsers) {
                userIdList.add(vXGroupUser.getUserId());
            }

            for (Long userId : userIdList) {
                VXUser vXUser = xUserService.readResource(userId);

                vXUsers.add(vXUser);
            }

            vXUserList.setVXUsers(vXUsers);
            vXUserList.setStartIndex(searchCriteria.getStartIndex());
            vXUserList.setResultSize(vXGroupUserList.getList().size());
            vXUserList.setTotalCount(vXGroupUserList.getTotalCount());
            vXUserList.setPageSize(searchCriteria.getMaxRows());
            vXUserList.setSortBy(vXGroupUserList.getSortBy());
            vXUserList.setSortType(vXGroupUserList.getSortType());
        } else {
            logger.debug("No users found for group id : {}", searchCriteria.getParamValue("xGroupId"));
        }

        return vXUserList;
    }

    public void modifyUserVisibility(HashMap<Long, Integer> visibilityMap) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        Set<Map.Entry<Long, Integer>> entries = visibilityMap.entrySet();

        for (Map.Entry<Long, Integer> entry : entries) {
            XXUser xUser = daoManager.getXXUser().getById(entry.getKey());
            VXUser vObj  = xUserService.populateViewBean(xUser);

            vObj.setIsVisible(entry.getValue());

            xUserService.updateResource(vObj);
        }
    }

    public void modifyGroupsVisibility(HashMap<Long, Integer> groupVisibilityMap) {
        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        Set<Map.Entry<Long, Integer>> entries = groupVisibilityMap.entrySet();

        for (Map.Entry<Long, Integer> entry : entries) {
            XXGroup xGroup = daoManager.getXXGroup().getById(entry.getKey());
            VXGroup vObj   = xGroupService.populateViewBean(xGroup);

            vObj.setIsVisible(entry.getValue());

            xGroupService.updateResource(vObj);
        }
    }

    // Module permissions
    public VXModuleDef createXModuleDefPermission(VXModuleDef vXModuleDef) {
        XXModuleDef xModDef = daoManager.getXXModuleDef().findByModuleName(vXModuleDef.getModule());

        if (xModDef != null) {
            throw restErrorUtil.createRESTException("Module Def with same name already exists.", MessageEnums.ERROR_DUPLICATE_OBJECT);
        }

        return xModuleDefService.createResource(vXModuleDef);
    }

    public VXModuleDef getXModuleDefPermission(Long id) {
        return xModuleDefService.readResource(id);
    }

    public VXModuleDef updateXModuleDefPermission(VXModuleDef vXModuleDef) {
        List<VXGroupPermission> groupPermListNew = vXModuleDef.getGroupPermList();
        List<VXUserPermission>  userPermListNew  = vXModuleDef.getUserPermList();
        XXModuleDef             xModuleDef       = daoManager.getXXModuleDef().getById(vXModuleDef.getId());

        if (!StringUtils.equals(xModuleDef.getModule(), vXModuleDef.getModule())) {
            throw restErrorUtil.createRESTException("Module name change is not allowed!", MessageEnums.DATA_NOT_UPDATABLE);
        }

        Map<Long, Object[]> xXPortalUserIdXXUserMap = xUserService.getXXPortalUserIdXXUserNameMap();
        Map<Long, String>   xXGroupMap              = xGroupService.getXXGroupIdNameMap();
        VXModuleDef         vModuleDefPopulateOld   = xModuleDefService.populateViewBean(xModuleDef, xXPortalUserIdXXUserMap, xXGroupMap, true);

        List<VXGroupPermission>      groupPermListOld = vModuleDefPopulateOld.getGroupPermList();
        List<VXUserPermission>       userPermListOld  = vModuleDefPopulateOld.getUserPermList();
        Map<Long, VXUserPermission>  userPermMapOld   = xUserPermissionService.convertVListToVMap(userPermListOld);
        Map<Long, VXGroupPermission> groupPermMapOld  = xGroupPermissionService.convertVListToVMap(groupPermListOld);

        if (groupPermMapOld != null && groupPermListNew != null) {
            for (VXGroupPermission newVXGroupPerm : groupPermListNew) {
                boolean           isExist        = false;
                VXGroupPermission oldVXGroupPerm = groupPermMapOld.get(newVXGroupPerm.getGroupId());

                if (oldVXGroupPerm != null && newVXGroupPerm.getGroupId().equals(oldVXGroupPerm.getGroupId()) && newVXGroupPerm.getModuleId().equals(oldVXGroupPerm.getModuleId())) {
                    isExist = true;

                    if (!newVXGroupPerm.getIsAllowed().equals(oldVXGroupPerm.getIsAllowed())) {
                        oldVXGroupPerm.setIsAllowed(newVXGroupPerm.getIsAllowed());

                        this.updateXGroupPermission(oldVXGroupPerm);
                    }
                }

                if (!isExist) {
                    this.createXGroupPermission(newVXGroupPerm);
                }
            }
        }

        if (userPermMapOld != null && userPermListNew != null) {
            for (VXUserPermission newVXUserPerm : userPermListNew) {
                boolean          isExist       = false;
                VXUserPermission oldVXUserPerm = userPermMapOld.get(newVXUserPerm.getUserId());

                if (oldVXUserPerm != null && newVXUserPerm.getUserId().equals(oldVXUserPerm.getUserId()) && newVXUserPerm.getModuleId().equals(oldVXUserPerm.getModuleId())) {
                    isExist = true;

                    if (!newVXUserPerm.getIsAllowed().equals(oldVXUserPerm.getIsAllowed())) {
                        oldVXUserPerm.setIsAllowed(newVXUserPerm.getIsAllowed());

                        this.updateXUserPermission(oldVXUserPerm);
                    }
                }

                if (!isExist) {
                    this.createXUserPermission(newVXUserPerm);
                }
            }
        }

        vXModuleDef = xModuleDefService.updateResource(vXModuleDef);

        return vXModuleDef;
    }

    public void deleteXModuleDefPermission(Long id, boolean force) {
        daoManager.getXXUserPermission().deleteByModuleId(id);
        daoManager.getXXGroupPermission().deleteByModuleId(id);

        xModuleDefService.deleteResource(id);
    }

    // User permission
    public VXUserPermission createXUserPermission(VXUserPermission vXUserPermission) {
        vXUserPermission = xUserPermissionService.createResource(vXUserPermission);

        Set<UserSessionBase> userSessions = sessionMgr.getActiveUserSessionsForPortalUserId(vXUserPermission.getUserId());

        if (!CollectionUtils.isEmpty(userSessions)) {
            for (UserSessionBase userSession : userSessions) {
                logger.info("Assigning permission to user who's found logged in into system, so updating permission in session of that user: [{}]", vXUserPermission.getUserName());

                sessionMgr.resetUserModulePermission(userSession);
            }
        }

        return vXUserPermission;
    }

    public VXUserPermission getXUserPermission(Long id) {
        return xUserPermissionService.readResource(id);
    }

    public VXUserPermission updateXUserPermission(VXUserPermission vXUserPermission) {
        vXUserPermission = xUserPermissionService.updateResource(vXUserPermission);

        Set<UserSessionBase> userSessions = sessionMgr.getActiveUserSessionsForPortalUserId(vXUserPermission.getUserId());

        if (!CollectionUtils.isEmpty(userSessions)) {
            for (UserSessionBase userSession : userSessions) {
                logger.info("Updating permission of user who's found logged in into system, so updating permission in session of user: [{}]", vXUserPermission.getUserName());

                sessionMgr.resetUserModulePermission(userSession);
            }
        }

        return vXUserPermission;
    }

    public void deleteXUserPermission(Long id, boolean force) {
        XXUserPermission xUserPermission = daoManager.getXXUserPermission().getById(id);

        if (xUserPermission == null) {
            throw restErrorUtil.createRESTException("No UserPermission found to delete, ID: " + id, MessageEnums.DATA_NOT_FOUND);
        }

        xUserPermissionService.deleteResource(id);

        Set<UserSessionBase> userSessions = sessionMgr.getActiveUserSessionsForPortalUserId(xUserPermission.getUserId());

        if (!CollectionUtils.isEmpty(userSessions)) {
            for (UserSessionBase userSession : userSessions) {
                logger.info("deleting permission of user who's found logged in into system, so updating permission in session of that user");

                sessionMgr.resetUserModulePermission(userSession);
            }
        }
    }

    // Group permission
    public VXGroupPermission createXGroupPermission(VXGroupPermission vXGroupPermission) {
        vXGroupPermission = xGroupPermissionService.createResource(vXGroupPermission);

        List<XXGroupUser> grpUsers = daoManager.getXXGroupUser().findByGroupId(vXGroupPermission.getGroupId());

        for (XXGroupUser xGrpUser : grpUsers) {
            Set<UserSessionBase> userSessions = sessionMgr.getActiveUserSessionsForXUserId(xGrpUser.getUserId());

            if (!CollectionUtils.isEmpty(userSessions)) {
                for (UserSessionBase userSession : userSessions) {
                    logger.info("Assigning permission to group, one of the user belongs to that group found logged in into system, so updating permission in session of that user");

                    sessionMgr.resetUserModulePermission(userSession);
                }
            }
        }

        return vXGroupPermission;
    }

    public VXGroupPermission getXGroupPermission(Long id) {
        return xGroupPermissionService.readResource(id);
    }

    public VXGroupPermission updateXGroupPermission(VXGroupPermission vXGroupPermission) {
        vXGroupPermission = xGroupPermissionService.updateResource(vXGroupPermission);

        List<XXGroupUser> grpUsers = daoManager.getXXGroupUser().findByGroupId(vXGroupPermission.getGroupId());

        for (XXGroupUser xGrpUser : grpUsers) {
            Set<UserSessionBase> userSessions = sessionMgr.getActiveUserSessionsForXUserId(xGrpUser.getUserId());

            if (!CollectionUtils.isEmpty(userSessions)) {
                for (UserSessionBase userSession : userSessions) {
                    logger.info("Assigning permission to group whose one of the user found logged in into system, so updating permission in session of that user");

                    sessionMgr.resetUserModulePermission(userSession);
                }
            }
        }

        return vXGroupPermission;
    }

    public void deleteXGroupPermission(Long id, boolean force) {
        XXGroupPermission xGrpPerm = daoManager.getXXGroupPermission().getById(id);

        if (xGrpPerm == null) {
            throw restErrorUtil.createRESTException("No GroupPermission object with ID: [" + id + "found.", MessageEnums.DATA_NOT_FOUND);
        }

        xGroupPermissionService.deleteResource(id);

        List<XXGroupUser> grpUsers = daoManager.getXXGroupUser().findByGroupId(xGrpPerm.getGroupId());

        for (XXGroupUser xGrpUser : grpUsers) {
            Set<UserSessionBase> userSessions = sessionMgr.getActiveUserSessionsForXUserId(xGrpUser.getUserId());

            if (!CollectionUtils.isEmpty(userSessions)) {
                for (UserSessionBase userSession : userSessions) {
                    logger.info("deleting permission of the group whose one of the user found logged in into system, so updating permission in session of that user");

                    sessionMgr.resetUserModulePermission(userSession);
                }
            }
        }
    }

    public void modifyUserActiveStatus(HashMap<Long, Integer> statusMap) {
        checkAdminAccess();

        UserSessionBase session     = ContextUtil.getCurrentUserSession();
        String          currentUser = null;

        if (session != null) {
            currentUser = session.getLoginId();

            if (currentUser == null || currentUser.trim().isEmpty()) {
                currentUser = null;
            }
        }

        if (currentUser == null) {
            return;
        }

        Set<Map.Entry<Long, Integer>> entries = statusMap.entrySet();

        for (Map.Entry<Long, Integer> entry : entries) {
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
                XXUser xUser = daoManager.getXXUser().getById(entry.getKey());

                if (xUser != null) {
                    VXPortalUser vXPortalUser = userMgr.getUserProfileByLoginId(xUser.getName());

                    if (vXPortalUser != null) {
                        if (vXPortalUser.getLoginId() != null && !vXPortalUser.getLoginId().equalsIgnoreCase(currentUser)) {
                            vXPortalUser.setStatus(entry.getValue());

                            userMgr.updateUser(vXPortalUser);
                        }
                    }
                }
            }
        }
    }

    public void checkAdminAccess() {
        UserSessionBase session = ContextUtil.getCurrentUserSession();

        if (session != null) {
            if (!session.isUserAdmin()) {
                VXResponse vXResponse = new VXResponse();

                vXResponse.setStatusCode(HttpServletResponse.SC_FORBIDDEN);
                vXResponse.setMsgDesc("Operation" + " denied. LoggedInUser=" + session.getXXPortalUser().getId() + " ,isn't permitted to perform the action.");

                throw restErrorUtil.generateRESTException(vXResponse);
            }
        } else {
            VXResponse vXResponse = new VXResponse();

            vXResponse.setStatusCode(HttpServletResponse.SC_UNAUTHORIZED); // user is null
            vXResponse.setMsgDesc("Bad Credentials");

            throw restErrorUtil.generateRESTException(vXResponse);
        }
    }

    public void checkAccess(VXUser vxUser) {
        UserSessionBase session = ContextUtil.getCurrentUserSession();

        if (session != null) {
            if (!hasAccessToGetUserInfo(vxUser)) {
                throw restErrorUtil.create403RESTException("Operation" + " denied. LoggedInUser=" + session.getXXPortalUser().getId() + " ,isn't permitted to perform the action.");
            }
        } else {
            VXResponse vXResponse = new VXResponse();

            vXResponse.setStatusCode(HttpServletResponse.SC_UNAUTHORIZED); // user is null
            vXResponse.setMsgDesc("Bad Credentials");

            throw restErrorUtil.generateRESTException(vXResponse);
        }
    }

    public void checkAccessRoles(List<String> stringRolesList) {
        UserSessionBase session = ContextUtil.getCurrentUserSession();

        if (session != null && stringRolesList != null) {
            if (!session.isUserAdmin() && !session.isKeyAdmin()) {
                throw restErrorUtil.create403RESTException("Permission denied. LoggedInUser=" + session.getXXPortalUser().getId() + " ,isn't permitted to perform the action.");
            } else {
                if (!"rangerusersync".equals(session.getXXPortalUser().getLoginId())) { // new logic for rangerusersync user
                    if (session.isUserAdmin() && (stringRolesList.contains(RangerConstants.ROLE_KEY_ADMIN) || stringRolesList.contains(RangerConstants.ROLE_KEY_ADMIN_AUDITOR))) {
                        throw restErrorUtil.create403RESTException("Permission denied. LoggedInUser=" + session.getXXPortalUser().getId() + " isn't permitted to perform the action.");
                    } else if (session.isKeyAdmin() && (stringRolesList.contains(RangerConstants.ROLE_SYS_ADMIN) || stringRolesList.contains(RangerConstants.ROLE_ADMIN_AUDITOR))) {
                        throw restErrorUtil.create403RESTException("Permission denied. LoggedInUser=" + session.getXXPortalUser().getId() + " isn't permitted to perform the action.");
                    }
                }
            }
        } else {
            VXResponse vXResponse = new VXResponse();

            vXResponse.setStatusCode(HttpServletResponse.SC_UNAUTHORIZED); // user is null or role is null
            vXResponse.setMsgDesc("Bad Credentials");

            throw restErrorUtil.generateRESTException(vXResponse);
        }
    }

    public VXStringList setUserRolesByExternalID(Long userId, List<VXString> vStringRolesList) {
        xaBizUtil.blockAuditorRoleUser();

        List<String> roleListNewProfile = new ArrayList<>();

        if (vStringRolesList != null) {
            for (VXString vXString : vStringRolesList) {
                roleListNewProfile.add(vXString.getValue());
            }
        }

        VXUser vXUser = getXUser(userId);

        checkAccessRoles(roleListNewProfile);

        if (vXUser != null && !roleListNewProfile.isEmpty()) {
            VXPortalUser oldUserProfile = userMgr.getUserProfileByLoginId(vXUser.getName());

            if (oldUserProfile != null) {
                denySelfRoleChange(oldUserProfile.getLoginId());
                updateUserRolesPermissions(oldUserProfile, roleListNewProfile);

                List<XXPortalUserRole> portalUserRoleList = daoManager.getXXPortalUserRole().findByUserId(oldUserProfile.getId());

                return getStringListFromUserRoleList(portalUserRoleList);
            } else {
                throw restErrorUtil.createRESTException("User ID doesn't exist.", MessageEnums.INVALID_INPUT_DATA);
            }
        } else {
            throw restErrorUtil.createRESTException("User ID doesn't exist.", MessageEnums.INVALID_INPUT_DATA);
        }
    }

    public VXStringList setUserRolesByName(String userName, List<VXString> vStringRolesList) {
        xaBizUtil.blockAuditorRoleUser();

        List<String> roleListNewProfile = new ArrayList<>();

        if (vStringRolesList != null) {
            for (VXString vXString : vStringRolesList) {
                roleListNewProfile.add(vXString.getValue());
            }
        }

        VXUser vXUser = getXUserByUserName(userName);

        checkAccessRoles(roleListNewProfile);

        if (vXUser != null && !roleListNewProfile.isEmpty()) {
            VXPortalUser oldUserProfile = userMgr.getUserProfileByLoginId(vXUser.getName());

            if (oldUserProfile != null) {
                denySelfRoleChange(oldUserProfile.getLoginId());
                updateUserRolesPermissions(oldUserProfile, roleListNewProfile);

                List<XXPortalUserRole> portalUserRoleList = daoManager.getXXPortalUserRole().findByUserId(oldUserProfile.getId());

                return getStringListFromUserRoleList(portalUserRoleList);
            } else {
                throw restErrorUtil.createRESTException("Login ID doesn't exist.", MessageEnums.INVALID_INPUT_DATA);
            }
        } else {
            throw restErrorUtil.createRESTException("Login ID doesn't exist.", MessageEnums.INVALID_INPUT_DATA);
        }
    }

    public VXStringList getUserRolesByExternalID(Long userId) {
        VXUser vXUser = getXUser(userId);

        if (vXUser == null) {
            throw restErrorUtil.createRESTException("Please provide a valid ID", MessageEnums.INVALID_INPUT_DATA);
        }

        checkAccess(vXUser);

        VXPortalUser oldUserProfile = userMgr.getUserProfileByLoginId(vXUser.getName());

        if (oldUserProfile != null) {
            List<XXPortalUserRole> portalUserRoleList = daoManager.getXXPortalUserRole().findByUserId(oldUserProfile.getId());

            return getStringListFromUserRoleList(portalUserRoleList);
        } else {
            throw restErrorUtil.createRESTException("User ID doesn't exist.", MessageEnums.INVALID_INPUT_DATA);
        }
    }

    public VXStringList getUserRolesByName(String userName) {
        if (userName != null && !userName.trim().isEmpty()) {
            VXUser vXUser = xUserService.getXUserByUserName(userName);

            checkAccess(vXUser);

            VXPortalUser vXPortalUser = userMgr.getUserProfileByLoginId(userName);

            if (vXPortalUser != null && vXPortalUser.getUserRoleList() != null) {
                List<XXPortalUserRole> portalUserRoleList = daoManager.getXXPortalUserRole().findByUserId(vXPortalUser.getId());

                return getStringListFromUserRoleList(portalUserRoleList);
            } else {
                throw restErrorUtil.createRESTException("Please provide a valid userName", MessageEnums.INVALID_INPUT_DATA);
            }
        } else {
            throw restErrorUtil.createRESTException("Please provide a valid userName", MessageEnums.INVALID_INPUT_DATA);
        }
    }

    public void updateUserRolesPermissions(VXPortalUser oldUserProfile, List<String> roleListNewProfile) {
        //update permissions start
        Collection<String> roleListUpdatedProfile = new ArrayList<>();

        if (oldUserProfile != null && oldUserProfile.getId() != null) {
            Collection<String> roleListOldProfile = oldUserProfile.getUserRoleList();

            if (roleListNewProfile != null && roleListOldProfile != null) {
                for (String role : roleListNewProfile) {
                    if (role != null && !roleListOldProfile.contains(role)) {
                        roleListUpdatedProfile.add(role);
                    }
                }
            }
        }

        if (!roleListUpdatedProfile.isEmpty()) {
            oldUserProfile.setUserRoleList(roleListUpdatedProfile);

            List<XXUserPermission> xuserPermissionList = daoManager.getXXUserPermission().findByUserPermissionId(oldUserProfile.getId());

            if (xuserPermissionList != null && !xuserPermissionList.isEmpty()) {
                for (XXUserPermission xXUserPermission : xuserPermissionList) {
                    if (xXUserPermission != null) {
                        xUserPermissionService.deleteResource(xXUserPermission.getId());
                    }
                }
            }

            assignPermissionToUser(oldUserProfile, true);

            if (!roleListUpdatedProfile.isEmpty()) {
                userMgr.updateRoles(oldUserProfile.getId(), oldUserProfile.getUserRoleList());
            }
        }
        //update permissions end
    }

    public VXStringList getStringListFromUserRoleList(List<XXPortalUserRole> listXXPortalUserRole) {
        if (listXXPortalUserRole == null) {
            return null;
        }

        List<VXString> xStrList = new ArrayList<>();

        for (XXPortalUserRole userRole : listXXPortalUserRole) {
            if (userRole != null) {
                VXString vXStr = new VXString();

                vXStr.setValue(userRole.getUserRole());
                xStrList.add(vXStr);
            }
        }

        return new VXStringList(xStrList);
    }

    public boolean hasAccess(String loginID) {
        UserSessionBase session = ContextUtil.getCurrentUserSession();

        if (session != null) {
            return session.isUserAdmin() || session.getLoginId().equalsIgnoreCase(loginID);
        }

        return false;
    }

    public VXUser getMaskedVXUser(VXUser vXUser) {
        if (vXUser != null) {
            if (vXUser.getGroupIdList() != null && !vXUser.getGroupIdList().isEmpty()) {
                vXUser.setGroupIdList(new ArrayList<>());
            }

            if (vXUser.getGroupNameList() != null && !vXUser.getGroupNameList().isEmpty()) {
                vXUser.setGroupNameList(getMaskedCollection(vXUser.getGroupNameList()));
            }

            if (vXUser.getUserRoleList() != null && !vXUser.getUserRoleList().isEmpty()) {
                vXUser.setUserRoleList(getMaskedCollection(vXUser.getUserRoleList()));
            }

            vXUser.setUpdatedBy(AppConstants.Masked_String);
        }

        return vXUser;
    }

    public VXGroup getMaskedVXGroup(VXGroup vXGroup) {
        if (vXGroup != null) {
            vXGroup.setUpdatedBy(AppConstants.Masked_String);
        }

        return vXGroup;
    }

    public VXGroupList lookupXGroups(SearchCriteria searchCriteria) {
        VXGroupList ret = null;

        try {
            HashMap<String, Object> searchParams  = searchCriteria.getParamList();
            String                  nameToLookFor = searchParams != null ? (String) searchParams.get("name") : null;
            VXGroup                 exactMatch    = null;

            if (StringUtils.isEmpty(searchCriteria.getSortBy())) {
                searchCriteria.setSortBy(nameToLookFor != null ? "name" : "id");
            }

            if (nameToLookFor != null) {
                exactMatch = getGroupByGroupName(nameToLookFor);

                for (Map.Entry<String, Object> entry : searchParams.entrySet()) {
                    if (exactMatch == null) {
                        break;
                    }

                    String paramName  = entry.getKey();
                    Object paramValue = entry.getValue();

                    switch (paramName.toLowerCase()) {
                        case "isvisible":
                            if (!Objects.equals(exactMatch.getIsVisible(), paramValue)) {
                                exactMatch = null;
                            }
                            break;

                        case "groupsource":
                            if (!Objects.equals(exactMatch.getGroupSource(), paramValue)) {
                                exactMatch = null;
                            }
                            break;

                        default:
                            // ignore
                            break;
                    }
                }
            }

            VXGroupList searchResult = xGroupService.searchXGroups(searchCriteria);

            if (exactMatch != null && exactMatch.getId() != null) {
                List<VXGroup> groups = searchResult.getList();

                if (!groups.isEmpty()) { // remove exactMatch from groups if it is present
                    boolean removed = false;

                    for (Iterator<VXGroup> iter = groups.iterator(); iter.hasNext(); ) {
                        VXGroup group = iter.next();

                        if (group != null && exactMatch.getId().equals(group.getId())) {
                            iter.remove();
                            removed = true;

                            break;
                        }
                    }

                    if (!removed) { // remove the last entry, if exactMatch was not removed above - to accomodate for add() below
                        groups.remove(groups.size() - 1);
                    }
                }

                groups.add(0, exactMatch);

                ret = new VXGroupList(groups);

                ret.setStartIndex(searchCriteria.getStartIndex());
                ret.setTotalCount(searchResult.getTotalCount());
                ret.setPageSize(searchCriteria.getMaxRows());
                ret.setSortBy(searchCriteria.getSortBy());
                ret.setSortType(searchCriteria.getSortType());
            } else {
                ret = searchResult;
            }
        } catch (Exception e) {
            logger.error("Error getting the exact match of group => {}", String.valueOf(e));
        }

        if (ret == null || ret.getList().isEmpty()) {
            searchCriteria.setSortBy("id");

            ret = xGroupService.searchXGroups(searchCriteria);
        }

        if (ret != null && ret.getListSize() > 0 && !hasAccessToModule(RangerConstants.MODULE_USER_GROUPS)) {
            for (VXGroup vXGroup : ret.getList()) {
                getMaskedVXGroup(vXGroup);
            }
        }

        return ret;
    }

    public Collection<String> getMaskedCollection(Collection<String> listunMasked) {
        List<String> listMasked = new ArrayList<>();

        if (listunMasked != null) {
            for (int i = 0; i < listunMasked.size(); i++) {
                listMasked.add(AppConstants.Masked_String);
            }
        }

        return listMasked;
    }

    public List<RangerPrincipal> getRangerPrincipals(SearchCriteria searchCriteria) {
        String searchString = (String) searchCriteria.getParamValue("name");
        int    startIdx     = searchCriteria.getStartIndex();
        int    maxRows      = searchCriteria.getMaxRows();

        return daoManager.getXXUser().lookupPrincipalByName(searchString, startIdx, maxRows);
    }

    public boolean hasAccessToModule(String moduleName) {
        UserSessionBase userSession = ContextUtil.getCurrentUserSession();

        if (userSession != null && userSession.getLoginId() != null) {
            VXUser vxUser = xUserService.getXUserByUserName(userSession.getLoginId());

            if (vxUser != null) {
                List<String> permissionList = daoManager.getXXModuleDef().findAccessibleModulesByUserId(userSession.getUserId(), vxUser.getId());

                return permissionList != null && permissionList.contains(moduleName);
            }
        }

        return false;
    }

    public long forceDeleteExternalGroups(List<Long> groupIds) {
        long groupsDeleted = 0;
        long failedDeletes = 0;
        long startTime     = Time.now();

        for (Long groupId : groupIds) {
            TransactionTemplate txTemplate = new TransactionTemplate(txManager);

            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                txTemplate.execute(status -> {
                    deleteXGroup(groupId, true);
                    return null;
                });

                groupsDeleted += 1;
            } catch (Throwable ex) {
                logger.error("forceDeleteExternalGroups(): Failed to delete group id: {}", groupId, ex);

                failedDeletes += 1;
            }
        }

        if (failedDeletes == 1) {
            logger.error("Failed to delete 1 group");
        } else if (failedDeletes > 1) {
            logger.error("Failed to delete {} groups", failedDeletes);
        }

        if (groupIds.size() == 1) {
            logger.info("Force Deletion of 1 group took {} milliseconds", (Time.now() - startTime));
        } else if (groupIds.size() > 1) {
            logger.info("Force Deletion of {} groups took {} milliseconds", groupIds.size(), (Time.now() - startTime));
        }

        return groupsDeleted;
    }

    public long forceDeleteExternalUsers(List<Long> userIds) {
        long usersDeleted  = 0;
        long failedDeletes = 0;
        long startTime     = Time.now();

        for (Long userId : userIds) {
            TransactionTemplate txTemplate = new TransactionTemplate(txManager);

            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            try {
                txTemplate.execute(status -> {
                    deleteXUser(userId, true);

                    return null;
                });

                usersDeleted += 1;
            } catch (Throwable ex) {
                logger.error("forceDeleteExternalUsers(): Failed to delete user id: {}", userId, ex);

                failedDeletes += 1;
            }
        }

        if (failedDeletes == 1) {
            logger.error("Failed to delete 1 user");
        } else if (failedDeletes > 1) {
            logger.error("Failed to delete {} users", failedDeletes);
        }

        if (userIds.size() == 1) {
            logger.info("Force Deletion of 1 user took {} milliseconds", (Time.now() - startTime));
        } else if (userIds.size() > 1) {
            logger.info("Force Deletion of {} users took {} milliseconds", userIds.size(), (Time.now() - startTime));
        }

        return usersDeleted;
    }

    public void restrictSelfAccountDeletion(String loginID) {
        UserSessionBase session = ContextUtil.getCurrentUserSession();

        if (session != null) {
            if (!session.isUserAdmin()) {
                VXResponse vXRes = new VXResponse();

                vXRes.setStatusCode(HttpServletResponse.SC_FORBIDDEN);
                vXRes.setMsgDesc("Operation denied. LoggedInUser= " + session.getXXPortalUser().getLoginId() + " isn't permitted to perform the action.");

                throw restErrorUtil.generateRESTException(vXRes);
            } else {
                if (StringUtils.isNotEmpty(loginID) && loginID.equals(session.getLoginId())) {
                    VXResponse vXResponse = new VXResponse();

                    vXResponse.setStatusCode(HttpServletResponse.SC_FORBIDDEN);
                    vXResponse.setMsgDesc("Operation denied. LoggedInUser= " + session.getXXPortalUser().getLoginId() + " isn't permitted to delete his own profile.");

                    throw restErrorUtil.generateRESTException(vXResponse);
                }
            }
        } else {
            VXResponse vXResponse = new VXResponse();

            vXResponse.setStatusCode(HttpServletResponse.SC_UNAUTHORIZED); // user is null
            vXResponse.setMsgDesc("Bad Credentials");

            throw restErrorUtil.generateRESTException(vXResponse);
        }
    }

    public VXUser createServiceConfigUser(String userName) {
        if (userName == null || "null".equalsIgnoreCase(userName) || userName.trim().isEmpty()) {
            logger.error("User Name: {}", userName);

            throw restErrorUtil.createRESTException("Please provide a valid username.", MessageEnums.INVALID_INPUT_DATA);
        }

        XXUser xxUser = daoManager.getXXUser().findByUserName(userName);

        if (xxUser == null) {
            transactionSynchronizationAdapter.executeOnTransactionCommit(new ExternalUserCreator(userName));
        }

        xxUser = daoManager.getXXUser().findByUserName(userName);

        VXUser vXUser = null;

        if (xxUser != null) {
            vXUser = xUserService.populateViewBean(xxUser);
        }

        return vXUser;
    }

    public VXUser createServiceConfigUserSynchronously(String userName) {
        if (userName == null || "null".equalsIgnoreCase(userName) || userName.trim().isEmpty()) {
            logger.error("User Name: {}", userName);

            throw restErrorUtil.createRESTException("Please provide a valid username.", MessageEnums.INVALID_INPUT_DATA);
        }

        VXUser vXUser = null;
        XXUser xxUser = daoManager.getXXUser().findByUserName(userName);

        if (xxUser == null) {
            ExternalUserCreator externalUserCreator = new ExternalUserCreator(userName);

            externalUserCreator.run();

            xxUser = daoManager.getXXUser().findByUserName(userName);
        }

        if (xxUser != null) {
            vXUser = xUserService.populateViewBean(xxUser);
        }

        return vXUser;
    }

    public void denySelfRoleChange(String userName) {
        UserSessionBase session = ContextUtil.getCurrentUserSession();

        if (session != null && session.getXXPortalUser() != null) {
            if (userName.equals(session.getXXPortalUser().getLoginId())) {
                throw restErrorUtil.create403RESTException("Permission denied. LoggedInUser=" + session.getXXPortalUser().getId() + " ,isn't permitted to change its own role.");
            }
        }
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED)
    public synchronized VXUgsyncAuditInfo postUserGroupAuditInfo(VXUgsyncAuditInfo vxUgsyncAuditInfo) {
        checkAdminAccess();

        //logger.info("post usersync audit info");
        vxUgsyncAuditInfo = xUgsyncAuditInfoService.createUgsyncAuditInfo(vxUgsyncAuditInfo);

        return vxUgsyncAuditInfo;
    }

    public Long getUserStoreVersion() {
        return daoManager.getXXGlobalState().getAppDataVersion(RANGER_GLOBAL_STATE_NAME_USER_GROUP);
    }

    public Set<UserInfo> getUsers() {
        return new HashSet<>(xUserService.getUsers());
    }

    public Set<GroupInfo> getGroups() {
        return new HashSet<>(xGroupService.getGroups());
    }

    public Map<String, Set<String>> getUserGroups() {
        return daoManager.getXXUser().findGroupsByUserIds();
    }

    public RangerUserStore getRangerUserStoreIfUpdated(Long lastKnownUserStoreVersion) {
        logger.debug("==> XUserMgr.getRangerUserStoreIfUpdated(lastKnownUserStoreVersion={})", lastKnownUserStoreVersion);

        RangerUserStore ret = RangerUserStoreCache.getInstance().getLatestRangerUserStoreOrCached(this);

        if (ret != null && Objects.equals(ret.getUserStoreVersion(), lastKnownUserStoreVersion)) {
            ret = null;
        }

        logger.debug("<== XUserMgr.getRangerUserStoreIfUpdated(lastKnownUserStoreVersion={}): ret={}", lastKnownUserStoreVersion, ret);

        return ret;
    }

    public int createOrUpdateXUsers(VXUserList users) {
        logger.debug("==> createOrUpdateXUsers(): Started");

        xaBizUtil.blockAuditorRoleUser();

        int ret = 0;

        for (VXUser vXUser : users.getList()) {
            final String userName  = vXUser == null ? null : vXUser.getName();
            final String firstName = vXUser == null ? null : vXUser.getFirstName();

            if (userName == null || "null".equalsIgnoreCase(userName) || userName.trim().isEmpty()) {
                logger.warn("Ignoring user {}: invalid username", userName);

                continue;
            }

            if (firstName == null || "null".equalsIgnoreCase(firstName) || firstName.trim().isEmpty()) {
                logger.warn("Ignoring user {}: invalid firstName {}", userName, firstName);

                continue;
            }

            checkAccess(vXUser);

            TransactionTemplate txTemplate = new TransactionTemplate(txManager);

            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                txTemplate.execute(status -> {
                    VXPortalUser vXPortalUser = userMgr.getUserProfileByLoginId(userName);

                    if (vXPortalUser == null) {
                        logger.debug("create user {}", userName);

                        createXUser(vXUser, userName);
                    } else {
                        logger.debug("Update user {}", userName);

                        updateXUser(vXUser, vXPortalUser);
                    }

                    return null;
                });
            } catch (Throwable ex) {
                logger.error("XUserMgr.createOrUpdateXUsers(): Failed to update DB for users: ", ex);

                throw restErrorUtil.createRESTException("Failed to create or update users ", MessageEnums.ERROR_CREATING_OBJECT);
            }

            ret++;
        }

        if (ret == 0) {
            logger.debug("<== createOrUpdateXUsers(): No users created or updated");

            return ret;
        }

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);

        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            txTemplate.execute((TransactionCallback<Void>) status -> {
                int       noOfRetries      = 0;
                Exception failureException;

                do {
                    noOfRetries++;

                    try {
                        daoManager.getXXGlobalState().onGlobalAppDataChange(RANGER_GLOBAL_STATE_NAME_USER_GROUP);

                        logger.debug("createOrUpdateXGroups(): Successfully updated x_ranger_global_state table");

                        return null;
                    } catch (Exception excp) {
                        logger.warn("createOrUpdateXGroups(): Failed to update x_ranger_global_state table and retry count =  {}", noOfRetries);

                        failureException = excp;
                    }
                }
                while (noOfRetries <= MAX_DB_TRANSACTION_RETRIES);

                logger.error("createOrUpdateXGroups(): Failed to update x_ranger_global_state table after max retries", failureException);

                throw new RuntimeException(failureException);
            });
        } catch (Throwable ex) {
            logger.error("XUserMgr.createOrUpdateXUsers(): Failed to update DB for GlobalState table ", ex);

            throw restErrorUtil.createRESTException("Failed to create or update users ", MessageEnums.ERROR_CREATING_OBJECT);
        }

        logger.debug("<== createOrUpdateXUsers(): Done");

        return ret;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED)
    public int createOrUpdateXGroups(VXGroupList groups) {
        for (VXGroup vXGroup : groups.getList()) {
            if (vXGroup == null || vXGroup.getName() == null || "null".equalsIgnoreCase(vXGroup.getName()) || vXGroup.getName().trim().isEmpty()) {
                logger.warn("Ignoring invalid groupname {}", vXGroup == null ? null : vXGroup.getName());

                continue;
            }

            createXGroupWithoutLogin(vXGroup);
        }

        updateUserStoreVersion("createOrUpdateXGroups(groupsCount=" + groups.getListSize() + ")");

        return groups.getListSize();
    }

    public int createOrDeleteXGroupUserList(List<GroupUserInfo> groupUserInfoList) {
        int  updatedGroups = 0;
        long mb            = 1024L * 1024L;

        if (logger.isDebugEnabled()) {
            logger.debug("==>> createOrDeleteXGroupUserList");
            logger.debug("Max memory = {} Free memory = {} Total memory = {}", Runtime.getRuntime().maxMemory() / mb, Runtime.getRuntime().freeMemory() / mb, Runtime.getRuntime().totalMemory() / mb);
        }

        checkAdminAccess();

        xaBizUtil.blockAuditorRoleUser();

        if (CollectionUtils.isNotEmpty(groupUserInfoList)) {
            logger.debug("No. of groups to be updated = {}", groupUserInfoList.size());

            Map<String, Long> usersFromDB = daoManager.getXXUser().getAllUserIds();

            if (MapUtils.isNotEmpty(usersFromDB)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("No. of users in DB = {}", usersFromDB.size());
                    logger.debug("After users from DB - Max memory = {} Free memory = {} Total memory = {}", Runtime.getRuntime().maxMemory() / mb, Runtime.getRuntime().freeMemory() / mb, Runtime.getRuntime().totalMemory() / mb);
                }

                for (GroupUserInfo groupUserInfo : groupUserInfoList) {
                    xGroupUserService.createOrDeleteXGroupUsers(groupUserInfo, usersFromDB);
                }

                updatedGroups = groupUserInfoList.size();
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("<<== createOrDeleteXGroupUserList");
            logger.debug("Max memory = {} Free memory = {} Total memory = {}", Runtime.getRuntime().maxMemory() / mb, Runtime.getRuntime().freeMemory() / mb, Runtime.getRuntime().totalMemory() / mb);
        }

        return updatedGroups;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRED)
    public List<String> updateUserRoleAssignments(UsersGroupRoleAssignments ugRoleAssignments) {
        List<String>        updatedUsers      = new ArrayList<>();
        List<String>        requestedUsers    = ugRoleAssignments.getUsers();
        Map<String, String> userMap           = ugRoleAssignments.getUserRoleAssignments();
        Map<String, String> groupMap          = ugRoleAssignments.getGroupRoleAssignments();
        Map<String, String> whiteListUserMap  = ugRoleAssignments.getWhiteListUserRoleAssignments();
        Map<String, String> whiteListGroupMap = ugRoleAssignments.getWhiteListGroupRoleAssignments();

        logger.debug("Request users for role updates = {}", requestedUsers);

        // For each user get groups and compute roles based on group role assignments
        for (String userName : requestedUsers) {
            VXPortalUser vXPortalUser = userMgr.getUserProfileByLoginId(userName);

            if (vXPortalUser == null) {
                logger.info("{} doesn't exist and hence ignoring role assignments", userName);

                continue;
            }

            if (vXPortalUser.getUserSource() != RangerCommonEnums.USER_EXTERNAL) {
                logger.info("{} is internal to ranger admin and hence ignoring role assignments", userName);

                continue;
            }

            logger.debug("Computing role for {}", userName);

            Set<String> groupUsers = getGroupsForUser(userName);
            String      userRole   = RangerConstants.ROLE_USER;

            if (MapUtils.isNotEmpty(userMap) && userMap.containsKey(userName)) {
                // Add the user role that is defined in user role assignments
                userRole = userMap.get(userName);
            } else if (MapUtils.isNotEmpty(groupMap) && CollectionUtils.isNotEmpty(groupUsers)) {
                for (String group : groupMap.keySet()) {
                    if (groupUsers.contains(group)) {
                        String value = groupMap.get(group);

                        if (value != null) {
                            userRole = value;
                            break;
                        }
                    }
                }
            }

            if (MapUtils.isNotEmpty(whiteListUserMap) && whiteListUserMap.containsKey(userName)) {
                userRole = whiteListUserMap.get(userName);
            } else if (MapUtils.isNotEmpty(whiteListGroupMap) && CollectionUtils.isNotEmpty(groupUsers)) {
                for (String group : whiteListGroupMap.keySet()) {
                    if (groupUsers.contains(group)) {
                        String value = whiteListGroupMap.get(group);

                        if (value != null) {
                            userRole = value;
                            break;
                        }
                    }
                }
            }

            if (!vXPortalUser.getUserRoleList().contains(userRole)) {
                logger.debug("Updating role for {} to {}", userName, userRole);

                //Update the role of the user only if newly computed role is different from the existing role.
                String updatedUser = setRolesByUserName(userName, Collections.singletonList(userRole));

                if (updatedUser != null) {
                    updatedUsers.add(updatedUser);
                }
            } else {
                logger.debug("Role for {} unchanged: {}", userName, userRole);
            }

            if (ugRoleAssignments.isReset()) { // use below data structure only when reset is true
                roleAssignmentUpdatedUsers.add(userName);
            }
        }

        // Reset the role of any other users that are not part of the updated role assignments rules
        if (ugRoleAssignments.isReset() && ugRoleAssignments.isLastPage()) {
            List<String> externalUsersWithNonUserRole = daoManager.getXXPortalUser().getNonUserRoleExternalUsers();

            logger.debug("Existing external users with roles excluding ROLE_USER role: {}", externalUsersWithNonUserRole);

            for (String userName : externalUsersWithNonUserRole) {
                if (!roleAssignmentUpdatedUsers.contains(userName)) {
                    logger.debug("Resetting to ROLE_USER for {}", userName);

                    String updatedUser = setRolesByUserName(userName, Collections.singletonList(RangerConstants.ROLE_USER));

                    if (updatedUser != null) {
                        updatedUsers.add(updatedUser);
                    }
                }
            }

            roleAssignmentUpdatedUsers.clear();
        }

        return updatedUsers;
    }

    public int updateDeletedUsers(Set<String> deletedUsers) {
        for (String deletedUser : deletedUsers) {
            XXUser xUser = daoManager.getXXUser().findByUserName(deletedUser);

            if (xUser != null) {
                VXUser vObj = xUserService.populateViewBean(xUser);

                vObj.setIsVisible(RangerCommonEnums.IS_HIDDEN);

                xUserService.updateResource(vObj);
            }
        }

        return deletedUsers.size();
    }

    public int updateDeletedGroups(Set<String> deletedGroups) {
        for (String deletedGroup : deletedGroups) {
            XXGroup xGroup = daoManager.getXXGroup().findByGroupName(deletedGroup);

            if (xGroup != null) {
                VXGroup vObj = xGroupService.populateViewBean(xGroup);

                vObj.setIsVisible(RangerCommonEnums.IS_HIDDEN);

                xGroupService.updateResource(vObj);
            }
        }

        return deletedGroups.size();
    }

    public VXUserList lookupXUsers(SearchCriteria searchCriteria) {
        VXUserList vXUserList = new VXUserList();

        if (StringUtils.isBlank(searchCriteria.getSortBy())) {
            searchCriteria.setSortBy("id");
        }

        vXUserList = xUserService.lookupXUsers(searchCriteria, vXUserList);

        return vXUserList;
    }

    public Map<String, Long> getUserCountByRole() {
        return daoManager.getXXPortalUser().getCountByUserRole();
    }

    protected VXGroupUser createXGroupUser(Long userId, Long groupId) {
        VXGroupUser vXGroupUser = new VXGroupUser();

        vXGroupUser.setParentGroupId(groupId);
        vXGroupUser.setUserId(userId);

        VXGroup vXGroup = xGroupService.readResource(groupId);

        vXGroupUser.setName(vXGroup.getName());

        vXGroupUser = xGroupUserService.createResource(vXGroupUser);

        return vXGroupUser;
    }

    protected void updateXgroupUserForGroupUpdate(VXGroup vXGroup) {
        List<XXGroupUser> grpUsers = daoManager.getXXGroupUser().findByGroupId(vXGroup.getId());

        if (CollectionUtils.isNotEmpty(grpUsers)) {
            for (XXGroupUser grpUser : grpUsers) {
                VXGroupUser vXGroupUser = xGroupUserService.populateViewBean(grpUser);

                vXGroupUser.setName(vXGroup.getName());

                updateXGroupUser(vXGroupUser);
            }
        }
    }

    protected void validatePassword(VXUser vXUser) {
        if (vXUser.getPassword() != null && !vXUser.getPassword().isEmpty()) {
            boolean checkPassword = vXUser.getPassword().trim().matches(StringUtil.VALIDATION_CRED);

            if (!checkPassword) {
                logger.warn("validatePassword(). Password should be minimum 8 characters, at least one uppercase letter, one lowercase letter and one numeric.");

                throw restErrorUtil.createRESTException("serverMsg.xuserMgrValidatePassword", MessageEnums.INVALID_PASSWORD, null, "Password should be minimum 8 characters, at least one uppercase letter, one lowercase letter and one numeric.", null);
            }
        } else {
            logger.warn("validatePassword(). Password cannot be blank/null.");

            throw restErrorUtil.createRESTException("serverMsg.xuserMgrValidatePassword", MessageEnums.INVALID_PASSWORD, null, "Password cannot be blank/null", null);
        }
    }

    private List<XXTrxLogV2> createOrDelGrpUserWithUpdatedGrpId(VXUser vXUser, Collection<Long> groupIdList, Long userId, List<Long> groupUsersToRemove) {
        Collection<String> groupNamesSet = new HashSet<>();
        List<XXTrxLogV2>   trxLogList    = new ArrayList<>();

        if (groupIdList != null) {
            SearchCriteria searchCriteria = new SearchCriteria();

            searchCriteria.addParam("xUserId", userId);

            VXGroupUserList   vXGroupUserList = xGroupUserService.searchXGroupUsers(searchCriteria);
            List<VXGroupUser> vXGroupUsers    = vXGroupUserList.getList();

            if (vXGroupUsers != null) {
                for (VXGroupUser eachVXGrpUser : vXGroupUsers) {
                    groupNamesSet.add(eachVXGrpUser.getName());
                }

                // Create
                for (Long groupId : groupIdList) {
                    boolean found = false;

                    for (VXGroupUser vXGroupUser : vXGroupUsers) {
                        if (groupId.equals(vXGroupUser.getParentGroupId())) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        VXGroupUser      vXGroupUser      = createXGroupUser(userId, groupId);
                        List<XXTrxLogV2> groupUserTrxLogs = xGroupUserService.getTransactionLog(vXGroupUser, null, OPERATION_CREATE_CONTEXT);

                        if (CollectionUtils.isNotEmpty(groupUserTrxLogs)) {
                            trxLogList.addAll(groupUserTrxLogs);
                        }

                        groupNamesSet.add(vXGroupUser.getName());
                    }
                }

                // Delete
                for (VXGroupUser vXGroupUser : vXGroupUsers) {
                    boolean found = false;

                    for (Long groupId : groupIdList) {
                        if (groupId.equals(vXGroupUser.getParentGroupId())) {
                            List<XXTrxLogV2> groupUserTrxLogs = xGroupUserService.getTransactionLog(vXGroupUser, null, OPERATION_UPDATE_CONTEXT);

                            if (CollectionUtils.isNotEmpty(groupUserTrxLogs)) {
                                trxLogList.addAll(groupUserTrxLogs);
                            }

                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        // TODO I've to get the transaction log from here.
                        List<XXTrxLogV2> groupUserTrxLogs = xGroupUserService.getTransactionLog(vXGroupUser, null, OPERATION_DELETE_CONTEXT);

                        if (CollectionUtils.isNotEmpty(groupUserTrxLogs)) {
                            trxLogList.addAll(groupUserTrxLogs);
                        }

                        groupUsersToRemove.add(vXGroupUser.getId());
                        // xGroupUserService.deleteResource(vXGroupUser.getId());
                        groupNamesSet.remove(vXGroupUser.getName());
                    }
                }
            } else {
                for (Long groupId : groupIdList) {
                    VXGroupUser      vXGroupUser      = createXGroupUser(userId, groupId);
                    List<XXTrxLogV2> groupUserTrxLogs = xGroupUserService.getTransactionLog(vXGroupUser, null, OPERATION_CREATE_CONTEXT);

                    if (CollectionUtils.isNotEmpty(groupUserTrxLogs)) {
                        trxLogList.addAll(groupUserTrxLogs);
                    }

                    groupNamesSet.add(vXGroupUser.getName());
                }
            }

            vXUser.setGroupIdList(groupIdList);
            vXUser.setGroupNameList(new ArrayList<>(groupNamesSet));
        } else {
            logger.debug("Group id list can't be null for user. Group user mapping not updated for user : {}", userId);
        }

        for (Long groupUserId : groupUsersToRemove) {
            xGroupUserService.deleteResource(groupUserId);
        }

        return trxLogList;
    }

    private boolean hasAccessToGetUserInfo(VXUser requestedVXUser) {
        UserSessionBase userSession = ContextUtil.getCurrentUserSession();

        if (userSession != null && userSession.getLoginId() != null) {
            VXUser loggedInVXUser = xUserService.getXUserByUserName(userSession.getLoginId());

            if (requestedVXUser != null && CollectionUtils.isNotEmpty(requestedVXUser.getUserRoleList()) && loggedInVXUser != null && loggedInVXUser.getUserRoleList().size() == 1) {
                if (loggedInVXUser.getUserRoleList().contains(RangerConstants.ROLE_USER)) {
                    return requestedVXUser.getId().equals(loggedInVXUser.getId());
                } else if (loggedInVXUser.getUserRoleList().contains(RangerConstants.ROLE_KEY_ADMIN) || loggedInVXUser.getUserRoleList().contains(RangerConstants.ROLE_KEY_ADMIN_AUDITOR)) {
                    return requestedVXUser.getUserRoleList().contains(RangerConstants.ROLE_KEY_ADMIN) || requestedVXUser.getUserRoleList().contains(RangerConstants.ROLE_KEY_ADMIN_AUDITOR) || requestedVXUser.getUserRoleList().contains(RangerConstants.ROLE_USER);
                } else if (loggedInVXUser.getUserRoleList().contains(RangerConstants.ROLE_SYS_ADMIN) || loggedInVXUser.getUserRoleList().contains(RangerConstants.ROLE_ADMIN_AUDITOR)) {
                    if (loggedInVXUser.getUserRoleList().contains(RangerConstants.ROLE_SYS_ADMIN) && "rangerusersync".equalsIgnoreCase(userSession.getLoginId())) {
                        return true;
                    } else {
                        return requestedVXUser.getUserRoleList().contains(RangerConstants.ROLE_SYS_ADMIN) || requestedVXUser.getUserRoleList().contains(RangerConstants.ROLE_ADMIN_AUDITOR) || requestedVXUser.getUserRoleList().contains(RangerConstants.ROLE_USER);
                    }
                }
            }
        }

        return false;
    }

    private void populatePageList(List<VXPermMap> permMapList, int startIndex, int pageSize, VXPermMapList vxPermMapList) {
        List<VXPermMap> onePageList = new ArrayList<>();

        for (int i = startIndex; i < pageSize + startIndex && i < permMapList.size(); i++) {
            VXPermMap vXPermMap = permMapList.get(i);

            onePageList.add(vXPermMap);
        }

        vxPermMapList.setVXPermMaps(onePageList);
        vxPermMapList.setStartIndex(startIndex);
        vxPermMapList.setPageSize(pageSize);
        vxPermMapList.setResultSize(onePageList.size());
        vxPermMapList.setTotalCount(permMapList.size());
    }

    private void populatePageList(List<VXAuditMap> auditMapList, int startIndex, int pageSize, VXAuditMapList vxAuditMapList) {
        List<VXAuditMap> onePageList = new ArrayList<>();

        for (int i = startIndex; i < pageSize + startIndex && i < auditMapList.size(); i++) {
            VXAuditMap vXAuditMap = auditMapList.get(i);

            onePageList.add(vXAuditMap);
        }

        vxAuditMapList.setVXAuditMaps(onePageList);
        vxAuditMapList.setStartIndex(startIndex);
        vxAuditMapList.setPageSize(pageSize);
        vxAuditMapList.setResultSize(onePageList.size());
        vxAuditMapList.setTotalCount(auditMapList.size());
    }

    private void blockIfZoneGroup(Long grpId) {
        List<XXSecurityZoneRefGroup> zoneRefGrpList = daoManager.getXXSecurityZoneRefGroup().findByGroupId(grpId);

        if (CollectionUtils.isNotEmpty(zoneRefGrpList)) {
            StringBuilder zones = new StringBuilder();

            for (XXSecurityZoneRefGroup zoneRefGrp : zoneRefGrpList) {
                XXSecurityZone xSecZone = daoManager.getXXSecurityZoneDao().getById(zoneRefGrp.getZoneId());

                if (zones.indexOf(xSecZone.getName()) < 0) {
                    zones.append(xSecZone.getName()).append(",");
                }
            }

            this.prepareAndThrow(zoneRefGrpList.get(0).getGroupName(), RangerConstants.MODULE_SECURITY_ZONE, zones, GROUP);
        }
    }

    private void blockIfZoneUser(Long id) {
        List<XXSecurityZoneRefUser> zoneRefUserList = daoManager.getXXSecurityZoneRefUser().findByUserId(id);

        if (CollectionUtils.isNotEmpty(zoneRefUserList)) {
            StringBuilder zones = new StringBuilder();

            for (XXSecurityZoneRefUser zoneRefUser : zoneRefUserList) {
                XXSecurityZone xSecZone = daoManager.getXXSecurityZoneDao().getById(zoneRefUser.getZoneId());

                if (zones.indexOf(xSecZone.getName()) < 0) {
                    zones.append(xSecZone.getName()).append(",");
                }
            }

            this.prepareAndThrow(zoneRefUserList.get(0).getUserName(), RangerConstants.MODULE_SECURITY_ZONE, zones, USER);
        }
    }

    private void blockIfRoleUser(Long id) {
        List<XXRoleRefUser> roleRefUsers = this.daoManager.getXXRoleRefUser().findByUserId(id);

        if (CollectionUtils.isNotEmpty(roleRefUsers)) {
            StringBuilder roles = new StringBuilder();

            for (XXRoleRefUser roleRefUser : roleRefUsers) {
                XXRole       xxRole   = this.daoManager.getXXRole().getById(roleRefUser.getRoleId());
                final String roleName = xxRole.getName();

                if (roles.indexOf(roleName) < 0) {
                    roles.append(roleName).append(",");
                }
            }

            final String roleRefUserName = roleRefUsers.get(0).getUserName();

            this.prepareAndThrow(roleRefUserName, RangerConstants.ROLE_FIELD, roles, USER);
        }
    }

    private void blockIfRoleGroup(Long id) {
        List<XXRoleRefGroup> roleRefGroups = this.daoManager.getXXRoleRefGroup().findByGroupId(id);

        if (CollectionUtils.isNotEmpty(roleRefGroups)) {
            StringBuilder roles = new StringBuilder();

            for (XXRoleRefGroup roleRefGroup : roleRefGroups) {
                XXRole       xxRole   = this.daoManager.getXXRole().getById(roleRefGroup.getRoleId());
                final String roleName = xxRole.getName();

                if (roles.indexOf(roleName) < 0) {
                    roles.append(roleName).append(",");
                }
            }

            final String roleRefGroupName = roleRefGroups.get(0).getGroupName();

            this.prepareAndThrow(roleRefGroupName, RangerConstants.ROLE_FIELD, roles, GROUP);
        }
    }

    private void prepareAndThrow(String userGrpName, String moduleName, StringBuilder rolesOrZones, String userOrGrp) {
        logger.error("Can Not Delete {}:{}", userOrGrp, userGrpName);

        VXResponse vXResponse = new VXResponse();

        vXResponse.setStatusCode(HttpServletResponse.SC_BAD_REQUEST);
        vXResponse.setMsgDesc("Can Not Delete " + userOrGrp + ": '" + userGrpName + "' as its present in " + moduleName + " : " + rolesOrZones.deleteCharAt(rolesOrZones.length() - 1));

        throw restErrorUtil.generateRESTException(vXResponse);
    }

    private <T extends RangerPolicyItem> void removeUserGroupReferences(List<T> policyItems, String user, String group) {
        List<T> itemsToRemove = null;

        for (T policyItem : policyItems) {
            if (StringUtils.isNotEmpty(user)) {
                policyItem.removeUser(user);
            }

            if (StringUtils.isNotEmpty(group)) {
                policyItem.removeGroup(group);
            }

            if (policyItem.getUsers().isEmpty() && policyItem.getGroups().isEmpty() && policyItem.getRoles().isEmpty()) {
                if (itemsToRemove == null) {
                    itemsToRemove = new ArrayList<>();
                }

                itemsToRemove.add(policyItem);
            }
        }

        if (CollectionUtils.isNotEmpty(itemsToRemove)) {
            policyItems.removeAll(itemsToRemove);
        }
    }

    private void createXUser(VXUser vXUser, String username) {
        logger.debug("Creating user: {}", username);

        VXPortalUser vXPortalUser = new VXPortalUser();

        vXPortalUser.setLoginId(username);
        vXPortalUser.setFirstName(vXUser.getFirstName());

        if ("null".equalsIgnoreCase(vXPortalUser.getFirstName())) {
            vXPortalUser.setFirstName("");
        }

        vXPortalUser.setLastName(vXUser.getLastName());

        if ("null".equalsIgnoreCase(vXPortalUser.getLastName())) {
            vXPortalUser.setLastName("");
        }

        String emailAddress = vXUser.getEmailAddress();

        if (StringUtils.isNotEmpty(emailAddress) && !stringUtil.validateEmail(emailAddress)) {
            logger.warn("Invalid email address:{}", emailAddress);

            throw restErrorUtil.createRESTException("Please provide valid email address.", MessageEnums.INVALID_INPUT_DATA);
        }

        vXPortalUser.setEmailAddress(emailAddress);

        if (vXPortalUser.getFirstName() != null && vXPortalUser.getLastName() != null && !vXPortalUser.getFirstName().trim().isEmpty() && !vXPortalUser.getLastName().trim().isEmpty()) {
            vXPortalUser.setPublicScreenName(vXPortalUser.getFirstName() + " " + vXPortalUser.getLastName());
        } else {
            vXPortalUser.setPublicScreenName(vXUser.getName());
        }

        vXPortalUser.setStatus(RangerCommonEnums.STATUS_ENABLED);
        vXPortalUser.setUserSource(RangerCommonEnums.USER_EXTERNAL);

        String saltEncodedpasswd = userMgr.encrypt(username, vXUser.getPassword());

        vXPortalUser.setPassword(saltEncodedpasswd);
        vXPortalUser.setUserRoleList(vXUser.getUserRoleList());

        XXPortalUser user = userMgr.mapVXPortalUserToXXPortalUser(vXPortalUser);

        user = daoManager.getXXPortalUser().create(user);

        // Create the UserRole for this user
        Collection<String> userRoleList = vXUser.getUserRoleList();

        if (userRoleList != null) {
            for (String userRole : userRoleList) {
                userMgr.addUserRole(user.getId(), userRole);
            }
        }

        XXUser xUser = daoManager.getXXUser().findByUserName(vXUser.getName());

        if (xUser == null) {
            vXUser = xUserService.createResource(vXUser);
        } else {
            vXUser = xUserService.populateViewBean(xUser);
        }

        xUserService.createTransactionLog(vXUser, null, OPERATION_CREATE_CONTEXT);

        assignPermissionToUser(vXPortalUser.getUserRoleList(), vXPortalUser.getId(), vXUser.getId(), true);

        logger.debug("Done creating user: {}", username);
    }

    private String setRolesByUserName(String userName, List<String> roleListNewProfile) {
        logger.debug("==> XUserMgr.setRolesByUserName({}, {})", userName, roleListNewProfile);

        String ret = null;

        xaBizUtil.blockAuditorRoleUser();

        if (roleListNewProfile == null) {
            roleListNewProfile = new ArrayList<>();
        }

        if (userName != null && !roleListNewProfile.isEmpty()) {
            checkAccessRoles(roleListNewProfile);

            VXPortalUser oldUserProfile = userMgr.getUserProfileByLoginId(userName);

            if (oldUserProfile != null) {
                denySelfRoleChange(oldUserProfile.getLoginId());
                updateUserRolesPermissions(oldUserProfile, roleListNewProfile);

                logger.info("<== XUserMgr.setRolesByUserName returned roles for {} are: {}", userName, roleListNewProfile);

                ret = userName;
            } else {
                logger.error("{}doesn't exist.", userName);
            }
        } else {
            logger.error("{}doesn't exist or new role assignments are empty", userName);
        }

        logger.debug("<== XUserMgr.setRolesByUserName({}, {}) ret = {}", userName, roleListNewProfile, ret);

        return ret;
    }

    private void assignPermissionToUser(Collection<String> vXPortalUserList, Long vXPortalUserId, Long xUserId, boolean isCreate) {
        HashMap<String, Long> moduleNameId = getAllModuleNameAndIdMap();

        if (moduleNameId != null && CollectionUtils.isNotEmpty(vXPortalUserList)) {
            for (String role : vXPortalUserList) {
                if (RangerConstants.VALID_USER_ROLE_LIST.contains(role)) {
                    createOrUpdateUserPermisson(vXPortalUserId, xUserId, moduleNameId.get(RangerConstants.MODULE_RESOURCE_BASED_POLICIES), isCreate);
                    createOrUpdateUserPermisson(vXPortalUserId, xUserId, moduleNameId.get(RangerConstants.MODULE_REPORTS), isCreate);

                    if (role.equals(RangerConstants.ROLE_USER)) {
                        createOrUpdateUserPermisson(vXPortalUserId, xUserId, moduleNameId.get(RangerConstants.MODULE_SECURITY_ZONE), isCreate);
                    } else {
                        createOrUpdateUserPermisson(vXPortalUserId, xUserId, moduleNameId.get(RangerConstants.MODULE_AUDIT), isCreate);
                        createOrUpdateUserPermisson(vXPortalUserId, xUserId, moduleNameId.get(RangerConstants.MODULE_USER_GROUPS), isCreate);

                        if (role.equals(RangerConstants.ROLE_SYS_ADMIN) || role.equals(RangerConstants.ROLE_ADMIN_AUDITOR)) {
                            createOrUpdateUserPermisson(vXPortalUserId, xUserId, moduleNameId.get(RangerConstants.MODULE_TAG_BASED_POLICIES), isCreate);
                            createOrUpdateUserPermisson(vXPortalUserId, xUserId, moduleNameId.get(RangerConstants.MODULE_SECURITY_ZONE), isCreate);
                        } else {
                            createOrUpdateUserPermisson(vXPortalUserId, xUserId, moduleNameId.get(RangerConstants.MODULE_KEY_MANAGER), isCreate);
                        }
                    }
                }
            }
        }
    }

    private void createOrUpdateUserPermisson(Long portalUserId, Long xUserId, Long moduleId, boolean isCreate) {
        VXUserPermission vXUserPermission;
        XXUserPermission xUserPermission = daoManager.getXXUserPermission().findByModuleIdAndPortalUserId(portalUserId, moduleId);

        if (xUserPermission == null) {
            vXUserPermission = new VXUserPermission();

            // When Creating XXUserPermission UI sends xUserId, to keep it consistent here xUserId should be used
            vXUserPermission.setUserId(xUserId);
            vXUserPermission.setIsAllowed(RangerCommonEnums.IS_ALLOWED);
            vXUserPermission.setModuleId(moduleId);

            try {
                vXUserPermission = this.createXUserPermission(vXUserPermission);

                logger.info("Permission assigned to user: [{}] For Module: [{}]", vXUserPermission.getUserName(), vXUserPermission.getModuleName());
            } catch (Exception e) {
                logger.error("Error while assigning permission to user: [{}] for module: [{}]", portalUserId, moduleId, e);
            }
        } else if (isCreate) {
            vXUserPermission = xUserPermissionService.populateViewBean(xUserPermission);

            vXUserPermission.setIsAllowed(RangerCommonEnums.IS_ALLOWED);

            vXUserPermission = this.updateXUserPermission(vXUserPermission);

            logger.info("Permission Updated for user: [{}] For Module: [{}]", vXUserPermission.getUserName(), vXUserPermission.getModuleName());
        }
    }

    private VXUser updateXUser(VXUser vXUser, VXPortalUser oldUserProfile) {
        logger.debug("Updating user: {}", vXUser.getName());

        VXPortalUser vXPortalUser = new VXPortalUser();

        if (oldUserProfile != null && oldUserProfile.getId() != null) {
            vXPortalUser.setId(oldUserProfile.getId());
        }

        vXPortalUser.setFirstName(vXUser.getFirstName());

        if ("null".equalsIgnoreCase(vXPortalUser.getFirstName())) {
            vXPortalUser.setFirstName("");
        }

        vXPortalUser.setLastName(vXUser.getLastName());

        if ("null".equalsIgnoreCase(vXPortalUser.getLastName())) {
            vXPortalUser.setLastName("");
        }

        vXPortalUser.setEmailAddress(vXUser.getEmailAddress());
        vXPortalUser.setLoginId(vXUser.getName());
        vXPortalUser.setStatus(vXUser.getStatus());
        vXPortalUser.setUserRoleList(vXUser.getUserRoleList());

        if (vXPortalUser.getFirstName() != null && vXPortalUser.getLastName() != null && !vXPortalUser.getFirstName().trim().isEmpty() && !vXPortalUser.getLastName().trim().isEmpty()) {
            vXPortalUser.setPublicScreenName(vXPortalUser.getFirstName() + " " + vXPortalUser.getLastName());
        } else {
            vXPortalUser.setPublicScreenName(vXUser.getName());
        }

        vXPortalUser.setUserSource(vXUser.getUserSource());
        vXPortalUser.setSyncSource(vXUser.getSyncSource());

        String hiddenPasswordString = PropertiesUtil.getProperty("ranger.password.hidden", "*****");
        String password             = vXUser.getPassword();

        if (oldUserProfile != null && password != null && password.equals(hiddenPasswordString)) {
            vXPortalUser.setPassword(oldUserProfile.getPassword());
        } else if (oldUserProfile != null && oldUserProfile.getUserSource() == RangerCommonEnums.USER_EXTERNAL && password != null) {
            vXPortalUser.setPassword(oldUserProfile.getPassword());

            logger.debug("User is trying to change external user password which we are not allowing it to change");
        } else if (password != null) {
            validatePassword(vXUser);
            vXPortalUser.setPassword(password);
        }

        XXPortalUser xXPortalUser = userMgr.updateUserWithPass(vXPortalUser);

        //update permissions start
        Collection<String> roleListUpdatedProfile = new ArrayList<>();

        if (oldUserProfile != null && oldUserProfile.getId() != null) {
            if (vXUser.getUserRoleList() != null) {
                Collection<String> roleListOldProfile = oldUserProfile.getUserRoleList();
                Collection<String> roleListNewProfile = vXUser.getUserRoleList();

                if (roleListNewProfile != null && roleListOldProfile != null) {
                    for (String role : roleListNewProfile) {
                        if (role != null && !roleListOldProfile.contains(role)) {
                            roleListUpdatedProfile.add(role);
                        }
                    }
                }
            }
        }

        if (!roleListUpdatedProfile.isEmpty()) {
            vXPortalUser.setUserRoleList(roleListUpdatedProfile);

            List<XXUserPermission> xuserPermissionList = daoManager.getXXUserPermission().findByUserPermissionId(vXPortalUser.getId());

            if (xuserPermissionList != null && !xuserPermissionList.isEmpty()) {
                for (XXUserPermission xXUserPermission : xuserPermissionList) {
                    if (xXUserPermission != null) {
                        try {
                            xUserPermissionService.deleteResource(xXUserPermission.getId());
                        } catch (Exception e) {
                            logger.error(e.getMessage());
                        }
                    }
                }
            }
        }

        //update permissions end
        Collection<String> roleList = new ArrayList<>();

        if (xXPortalUser != null) {
            roleList = userMgr.getRolesForUser(xXPortalUser);
        }

        if (roleList == null || roleList.isEmpty()) {
            roleList = new ArrayList<>();

            roleList.add(RangerConstants.ROLE_USER);
        }

        // TODO I've to get the transaction log from here.
        // There is nothing to log anything in XXUser so far.
        XXUser xUser = daoManager.getXXUser().findByUserName(vXUser.getName());

        if (xUser == null) {
            logger.warn("Could not find corresponding xUser for username: [{}], So not updating this user", vXPortalUser.getLoginId());

            return vXUser;
        }

        VXUser existing = xUserService.populateViewBean(xUser);

        logger.info("xUser.getName() = {} vXUser.getName() = {}", xUser.getName(), vXUser.getName());

        vXUser.setId(xUser.getId());

        try {
            vXUser = xUserService.updateResource(vXUser);
        } catch (Exception ex) {
            logger.warn("Failed to update username {}", vXUser.getName());
            logger.debug("Failed to update username {}", vXUser.getName(), ex);
        }

        vXUser.setUserRoleList(roleList);

        if (oldUserProfile != null) {
            if (oldUserProfile.getUserSource() == RangerCommonEnums.USER_APP) {
                vXUser.setPassword(password);
            } else if (oldUserProfile.getUserSource() == RangerCommonEnums.USER_EXTERNAL) {
                vXUser.setPassword(oldUserProfile.getPassword());
            }
        }

        List<XXTrxLogV2> trxLogList = xUserService.getTransactionLog(vXUser, existing, OPERATION_UPDATE_CONTEXT);

        vXUser.setPassword(hiddenPasswordString);

        Long userId = vXUser.getId();

        assignPermissionToUser(vXPortalUser.getUserRoleList(), vXPortalUser.getId(), userId, true);

        xaBizUtil.createTrxLog(trxLogList);

        logger.debug("Done updating user: {}", vXUser.getName());

        return vXUser;
    }

    private void updateUserStoreVersion(String label) {
        try {
            daoManager.getXXGlobalState().onGlobalAppDataChange(RANGER_GLOBAL_STATE_NAME_USER_GROUP);
        } catch (Exception excp) {
            logger.error("{}: userStore version update failed", label, excp);
        }
    }

    private class ExternalUserCreator implements Runnable {
        private final String userName;

        ExternalUserCreator(String user) {
            this.userName = user;
        }

        @Override
        public void run() {
            createExternalUser();
        }

        private void createExternalUser() {
            logger.debug("==> ExternalUserCreator.createExternalUser(username={}", userName);

            XXPortalUser xXPortalUser = daoManager.getXXPortalUser().findByLoginId(userName);

            if (xXPortalUser == null) {
                logger.debug("createExternalUser(): Couldn't find {} and hence creating user in x_portal_user table", userName);

                VXPortalUser vXPortalUser = new VXPortalUser();

                vXPortalUser.setLoginId(userName);
                vXPortalUser.setUserSource(RangerCommonEnums.USER_EXTERNAL);

                ArrayList<String> roleList = new ArrayList<>();

                roleList.add(RangerConstants.ROLE_USER);

                vXPortalUser.setUserRoleList(roleList);

                xXPortalUser = userMgr.mapVXPortalUserToXXPortalUser(vXPortalUser);

                try {
                    xXPortalUser = userMgr.createUser(xXPortalUser, RangerCommonEnums.STATUS_ENABLED, roleList);

                    logger.debug("createExternalUser(): Successfully created user in x_portal_user table {}", xXPortalUser.getLoginId());
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to create user " + userName + " in x_portal_user table. retrying", ex);
                }
            }

            VXUser createdXUser   = null;
            String actualPassword = "";
            XXUser xXUser         = daoManager.getXXUser().findByUserName(userName);

            if (xXUser == null) {
                VXUser vXUser = new VXUser();

                vXUser.setName(userName);
                vXUser.setUserSource(RangerCommonEnums.USER_EXTERNAL);
                vXUser.setDescription(vXUser.getName());

                actualPassword = vXUser.getPassword();

                try {
                    createdXUser = xUserService.createResource(vXUser);

                    logger.debug("createExternalUser(): Successfully created user in x_user table {}", vXUser.getName());
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to create user " + userName + " in x_user table. retrying", ex);
                }
            }

            if (createdXUser != null) {
                logger.info("User created: {}", createdXUser.getName());

                try {
                    createdXUser.setPassword(actualPassword);

                    xUserService.createTransactionLog(createdXUser, null, OPERATION_CREATE_CONTEXT);

                    String hiddenPassword = PropertiesUtil.getProperty("ranger.password.hidden", "*****");

                    createdXUser.setPassword(hiddenPassword);
                } catch (Exception ex) {
                    throw new RuntimeException("Error while creating trx logs for user: " + createdXUser.getName(), ex);
                }

                try {
                    VXPortalUser createdXPortalUser = userMgr.mapXXPortalUserToVXPortalUserForDefaultAccount(xXPortalUser);

                    assignPermissionToUser(createdXPortalUser, true);
                } catch (Exception ex) {
                    throw new RuntimeException("Error while assigning permissions to user: " + createdXUser.getName(), ex);
                }
            }

            logger.debug("<== ExternalUserCreator.createExternalUser(username={}", userName);
        }
    }
}
