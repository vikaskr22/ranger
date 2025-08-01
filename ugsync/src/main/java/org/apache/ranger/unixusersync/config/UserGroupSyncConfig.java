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

package org.apache.ranger.unixusersync.config;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.ranger.credentialapi.CredentialReader;
import org.apache.ranger.plugin.util.RangerCommonConstants;
import org.apache.ranger.plugin.util.XMLUtils;
import org.apache.ranger.ugsyncutil.util.UgsyncCommonConstants;
import org.apache.ranger.unixusersync.ha.UserSyncHAInitializerImpl;
import org.apache.ranger.usergroupsync.UserGroupSink;
import org.apache.ranger.usergroupsync.UserGroupSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

public class UserGroupSyncConfig {
    public static final  String CONFIG_FILE                            = "ranger-ugsync-site.xml";
    public static final  String DEFAULT_CONFIG_FILE                    = "ranger-ugsync-default.xml";
    public static final  String UGSYNC_ENABLED_PROP                    = "ranger.usersync.enabled";
    /* Unix Configs */
    public static final  String UGSYNC_MIN_USERID_PROP                 = "ranger.usersync.unix.minUserId";
    public static final  String UGSYNC_MIN_GROUPID_PROP                = "ranger.usersync.unix.minGroupId";
    public static final  String UGSYNC_UNIX_PASSWORD_FILE              = "ranger.usersync.unix.password.file";
    public static final  String UGSYNC_UNIX_GROUP_FILE                 = "ranger.usersync.unix.group.file";
    /* Unix Defaults */
    public static final  String DEFAULT_UGSYNC_UNIX_GROUP_FILE         = "/etc/group";
    public static final  String DEFAULT_UGSYNC_UNIX_PASSWORD_FILE      = "/etc/passwd";
    public static final  String DEFAULT_UGSYNC_MIN_GROUPID             = "0";
    /* File Sync Configs */
    public static final  String UGSYNC_SOURCE_FILE_PROC                = "ranger.usersync.filesource.file";
    public static final  String UGSYNC_SOURCE_FILE_DELIMITER           = "ranger.usersync.filesource.text.delimiter";
    public static final  String UGSYNC_SOURCE_FILE_DELIMITERER         = "ranger.usersync.filesource.text.delimiterer";
    /* Metrics */
    public static final  String UGSYNC_METRICS_ENABLED_PROP            = "ranger.usersync.metrics.enabled";
    /* Policy Manager Configs */
    public static final  String UGSYNC_PM_URL_PROP                     = "ranger.usersync.policymanager.baseURL";
    public static final  String UGSYNC_MAX_RECORDS_PER_API_CALL_PROP   = "ranger.usersync.policymanager.maxrecordsperapicall";
    public static final  String UGSYNC_MOCK_RUN_PROP                   = "ranger.usersync.policymanager.mockrun";
    public static final  String UGSYNC_TEST_RUN_PROP                   = "ranger.usersync.policymanager.testrun";
    /* Other Configs */
    public static final  String UGSYNC_SERVER_HA_ENABLED_PARAM         = "ranger-ugsync.server.ha.enabled";
    public static final  String UGSYNC_NAME_VALIDATION_ENABLED         = "ranger.usersync.name.validation.enabled";
    public static final  String UGSYNC_SYNC_SOURCE_VALIDATION_ENABLED  = "ranger.usersync.syncsource.validation.enabled";
    private static final Logger LOG                                    = LoggerFactory.getLogger(UserGroupSyncConfig.class);
    private static final String CORE_SITE_CONFIG_FILE                  = "core-site.xml";
    private static final String UGSYNC_SOURCE_CLASS_PARAM              = "ranger.usersync.source.impl.class";
    private static final String UGSYNC_SINK_CLASS_PARAM                = "ranger.usersync.sink.impl.class";
    private static final String UGSYNC_SOURCE_CLASS                    = "org.apache.ranger.unixusersync.process.UnixUserGroupBuilder";
    private static final String UGSYNC_SINK_CLASS                      = "org.apache.ranger.unixusersync.process.PolicyMgrUserGroupBuilder";
    private static final String LGSYNC_SOURCE_CLASS                    = "org.apache.ranger.ldapusersync.process.LdapUserGroupBuilder";
    /* LDAP Configs */
    private static final String LGSYNC_LDAP_URL                        = "ranger.usersync.ldap.url";
    private static final String LGSYNC_LDAP_AUTHENTICATION_MECHANISM   = "ranger.usersync.ldap.authentication.mechanism";
    private static final String LGSYNC_REFERRAL                        = "ranger.usersync.ldap.referral";
    private static final String LGSYNC_LDAP_BIND_DN                    = "ranger.usersync.ldap.binddn";
    private static final String LGSYNC_LDAP_BIND_ALIAS                 = "ranger.usersync.ldap.bindalias";
    private static final String LGSYNC_LDAP_BIND_PASSWORD              = "ranger.usersync.ldap.ldapbindpassword";
    private static final String LGSYNC_SEARCH_BASE                     = "ranger.usersync.ldap.searchBase";
    private static final String LGSYNC_USER_SEARCH_BASE                = "ranger.usersync.ldap.user.searchbase";
    private static final String LGSYNC_USER_SEARCH_SCOPE               = "ranger.usersync.ldap.user.searchscope";
    private static final String LGSYNC_USER_OBJECT_CLASS               = "ranger.usersync.ldap.user.objectclass";
    private static final String LGSYNC_USER_SEARCH_FILTER              = "ranger.usersync.ldap.user.searchfilter";
    private static final String LGSYNC_USER_NAME_ATTRIBUTE             = "ranger.usersync.ldap.user.nameattribute";
    private static final String LGSYNC_USER_GROUP_NAME_ATTRIBUTE       = "ranger.usersync.ldap.user.groupnameattribute";
    private static final String LGSYNC_OTHER_USER_ATTRIBUTES           = "ranger.usersync.ldap.user.otherattributes";
    private static final String LGSYNC_USER_CLOUDID_ATTRIBUTE          = "ranger.usersync.ldap.user.cloudid.attribute";
    private static final String LGSYNC_USER_CLOUDID_ATTRIBUTE_DATATYPE = "ranger.usersync.ldap.user.cloudid.attribute.datatype";

    /* LDAP Defaults */
    /**
     * ranger.usersync.user.searchenabled is used only when group search first is enabled to get username from -
     * 1. the group member attribute of the group or
     * 2. the additional user search based on the user attribute configuration
     */
    private static final String  LGSYNC_USER_SEARCH_ENABLED                                         = "ranger.usersync.user.searchenabled";
    private static final String  LGSYNC_GROUP_SEARCH_BASE                                           = "ranger.usersync.group.searchbase";
    private static final String  LGSYNC_GROUP_SEARCH_SCOPE                                          = "ranger.usersync.group.searchscope";
    private static final String  LGSYNC_GROUP_OBJECT_CLASS                                          = "ranger.usersync.group.objectclass";
    private static final String  LGSYNC_GROUP_SEARCH_FILTER                                         = "ranger.usersync.group.searchfilter";
    private static final String  LGSYNC_GROUP_NAME_ATTRIBUTE                                        = "ranger.usersync.group.nameattribute";
    private static final String  LGSYNC_GROUP_MEMBER_ATTRIBUTE_NAME                                 = "ranger.usersync.group.memberattributename";
    private static final String  LGSYNC_GROUP_SEARCH_ENABLED                                        = "ranger.usersync.group.searchenabled";
    private static final String  LGSYNC_GROUP_SEARCH_FIRST_ENABLED                                  = "ranger.usersync.group.search.first.enabled";
    private static final String  LGSYNC_GROUPNAMES                                                  = "ranger.usersync.ldap.groupnames";
    private static final String  LGSYNC_OTHER_GROUP_ATTRIBUTES                                      = "ranger.usersync.ldap.group.otherattributes";
    private static final String  LGSYNC_GROUP_CLOUDID_ATTRIBUTE                                     = "ranger.usersync.ldap.group.cloudid.attribute";
    private static final String  LGSYNC_GROUP_CLOUDID_ATTRIBUTE_DATATYPE                            = "ranger.usersync.ldap.group.cloudid.attribute.datatype";
    private static final String  LGSYNC_GROUP_HIERARCHY_LEVELS                                      = "ranger.usersync.ldap.grouphierarchylevels";
    private static final String  LGSYNC_LDAP_BIND_KEYSTORE                                          = "ranger.usersync.credstore.filename";
    private static final String  LGSYNC_LDAP_DELTASYNC_ENABLED                                      = "ranger.usersync.ldap.deltasync";
    private static final String  LGSYNC_LDAP_STARTTLS_ENABLED                                       = "ranger.usersync.ldap.starttls";
    private static final String  UGSYNC_SLEEP_LDAP_FORCE_TIME_IN_MILLIS_BETWEEN_CYCLE_PARAM_ENABLED = "ranger.usersync.ldap.force.sleeptimeinmillisbetweensynccycle.enabled";
    private static final String  DEFAULT_AUTHENTICATION_MECHANISM                                   = "simple";
    private static final String  DEFAULT_USER_OBJECT_CLASS                                          = "person";
    private static final String  DEFAULT_USER_NAME_ATTRIBUTE                                        = "cn";
    private static final String  DEFAULT_USER_GROUP_NAME_ATTRIBUTE                                  = "memberof,ismemberof";
    private static final String  DEFAULT_USER_CLOUDID_ATTRIBUTE                                     = "objectid";
    private static final String  DEFAULT_USER_CLOUDID_ATTRIBUTE_DATATYPE                            = "byte[]";
    private static final String  DEFAULT_OTHER_USER_ATTRIBUTES                                      = "userurincipaluame,";
    private static final String  DEFAULT_LGSYNC_GROUP_OBJECT_CLASS                                  = "groupofnames";
    private static final String  DEFAULT_LGSYNC_GROUP_NAME_ATTRIBUTE                                = "cn";
    private static final String  DEFAULT_LGSYNC_GROUP_MEMBER_ATTRIBUTE_NAME                         = "member";
    private static final String  DEFAULT_GROUP_CLOUDID_ATTRIBUTE                                    = "objectid";
    private static final String  DEFAULT_GROUP_CLOUDID_ATTRIBUTE_DATATYPE                           = "byte[]";
    private static final String  DEFAULT_OTHER_GROUP_ATTRIBUTES                                     = "displayname,";
    private static final String  DEFAULT_LGSYNC_REFERRAL                                            = "ignore";
    private static final int     DEFAULT_LGSYNC_GROUP_HIERARCHY_LEVELS                              = 0;
    private static final int     DEFAULT_LGSYNC_PAGED_RESULTS_SIZE                                  = 500;
    private static final boolean DEFAULT_LGSYNC_LDAP_DELTASYNC_ENABLED                              = false;
    private static final boolean DEFAULT_LGSYNC_LDAP_STARTTLS_ENABLED                               = false;
    private static final boolean DEFAULT_LGSYNC_PAGED_RESULTS_ENABLED                               = true;
    private static final boolean DEFAULT_LGSYNC_GROUP_SEARCH_ENABLED                                = true;
    private static final boolean DEFAULT_LGSYNC_USER_SEARCH_ENABLED                                 = true;
    private static final boolean DEFAULT_LGSYNC_GROUP_SEARCH_FIRST_ENABLED                          = true;
    private static final String  UGSYNC_GROUP_ENUMERATE_ENABLED                                     = "ranger.usersync.group.enumerate";
    private static final String  UGSYNC_GROUP_ENUMERATE_GROUPS                                      = "ranger.usersync.group.enumerategroup";
    private static final String  UGSYNC_UNIX_BACKEND                                                = "ranger.usersync.unix.backend";
    private static final String  DEFAULT_UGSYNC_UNIX_BACKEND                                        = "passwd";
    private static final String  UGSYNC_UPDATE_MILLIS_MIN                                           = "ranger.usersync.unix.updatemillismin";
    private static final String  DEFAULT_USER_GROUP_TEXTFILE_DELIMITER                              = ",";

    /* Role Assignments */
    private static final String ROLE_ASSIGNMENT_LIST_DELIMITER                  = "ranger.usersync.role.assignment.list.delimiter";
    private static final String USERS_GROUPS_ASSIGNMENT_LIST_DELIMITER          = "ranger.usersync.users.groups.assignment.list.delimiter";
    private static final String USERNAME_GROUPNAME_ASSIGNMENT_LIST_DELIMITER    = "ranger.usersync.username.groupname.assignment.list.delimiter";
    private static final String GROUP_BASED_ROLE_ASSIGNMENT_RULES               = "ranger.usersync.group.based.role.assignment.rules";
    private static final String WHITELIST_USER_ROLE_ASSIGNMENT_RULES            = "ranger.usersync.whitelist.users.role.assignment.rules";
    private static final String DEFAULT_WHITELIST_USER_ROLE_ASSIGNMENT_RULES    = "&ROLE_SYS_ADMIN:u:admin,rangerusersync,rangertagsync&ROLE_KEY_ADMIN:u:keyadmin";
    private static final String UGSYNC_METRICS_FILENAME                         = "ranger.usersync.metrics.filename";
    private static final String DEFAULT_UGSYNC_METRICS_FILENAME                 = "ranger_usersync_metric.json";
    private static final String UGSYNC_METRICS_FILEPATH                         = "ranger.usersync.metrics.filepath";
    private static final String UGSYNC_METRICS_FREQUENCY_TIME_IN_MILLIS_PARAM   = "ranger.usersync.metrics.frequencytimeinmillis";
    private static final String DEFAULT_UGSYNC_METRICS_FILEPATH                 = "/tmp/";
    private static final long   DEFAULT_UGSYNC_METRICS_FREQUENCY_TIME_IN_MILLIS = 10_000L;
    private static final String SYNC_POLICY_MGR_KEYSTORE                        = "ranger.usersync.policymgr.keystore";
    private static final String SYNC_POLICY_MGR_ALIAS                           = "ranger.usersync.policymgr.alias";
    private static final String SYNC_POLICY_MGR_PASSWORD                        = "ranger.usersync.policymgr.password";
    private static final String SYNC_POLICY_MGR_USERNAME                        = "ranger.usersync.policymgr.username";
    private static final String SYNC_POLICY_MGR_MAX_RETRY_ATTEMPTS              = "ranger.usersync.policymgr.max.retry.attempts";
    private static final String SYNC_POLICY_MGR_RETRY_INTERVAL_MS               = "ranger.usersync.policymgr.retry.interval.ms";
    private static final String DEFAULT_POLICYMGR_USERNAME                      = "rangerusersync";
    private static final String UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_PARAM = "ranger.usersync.sleeptimeinmillisbetweensynccycle";

    private static final long    UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_MIN_VALUE             = 60_000L;
    private static final long    UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_UNIX_DEFAULT_VALUE    = 60_000L;
    private static final long    UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_LDAP_DEFAULT_VALUE    = 3_600_000L;
    private static final long    DEFAULT_UGSYNC_UPDATE_MILLIS_MIN                                = 60_000;
    private static final long    UGSYNC_INIT_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_MIN_VALUE_FOR_HA = 5_000L;
    private static final boolean DEFAULT_UGSYNC_NAME_VALIDATION_ENABLED                          = false;
    private static final boolean DEFAULT_UGSYNC_SYNC_SOURCE_VALIDATION_ENABLED                   = true;

    private static final String SYNC_SOURCE                         = "ranger.usersync.sync.source";
    private static final String LGSYNC_PAGED_RESULTS_ENABLED        = "ranger.usersync.pagedresultsenabled";
    private static final String LGSYNC_PAGED_RESULTS_SIZE           = "ranger.usersync.pagedresultssize";
    private static final String UGSYNC_DELETES_ENABLED              = "ranger.usersync.deletes.enabled";
    private static final String UGSYNC_DELETES_FREQUENCY            = "ranger.usersync.deletes.frequency";
    private static final String USERSYNC_RANGER_COOKIE_ENABLED_PROP = "ranger.usersync.cookie.enabled";
    private static final String RANGER_ADMIN_COOKIE_NAME_PROPS      = "ranger.usersync.dest.ranger.session.cookie.name";

    private static final boolean DEFAULT_UGSYNC_DELETES_ENABLED   = false;
    private static final long    DEFAULT_UGSYNC_DELETES_FREQUENCY = 10L; // After every 10 sync cycles

    /* SSL Configs */
    private static final String SSL_KEYSTORE_FILE_TYPE_PARAM       = "ranger.keystore.file.type";
    private static final String SSL_TRUSTSTORE_FILE_TYPE_PARAM     = "ranger.truststore.file.type";
    private static final String SSL_KEYSTORE_PATH_PARAM            = "ranger.usersync.keystore.file";
    private static final String SSL_KEYSTORE_PATH_PASSWORD_PARAM   = "ranger.usersync.keystore.password";
    private static final String SSL_TRUSTSTORE_PATH_PARAM          = "ranger.usersync.truststore.file";
    private static final String SSL_TRUSTSTORE_PATH_PASSWORD_PARAM = "ranger.usersync.truststore.password";
    private static final String SSL_KEYSTORE_PATH_PASSWORD_ALIAS   = "usersync.ssl.key.password";
    private static final String SSL_TRUSTSTORE_PATH_PASSWORD_ALIAS = "usersync.ssl.truststore.password";

    private static volatile UserGroupSyncConfig me;
    private final           Properties          prop = new Properties();
    private                 Configuration       userGroupConfig;

    private UserGroupSyncConfig() {
        init();
    }

    public static UserGroupSyncConfig getInstance() {
        UserGroupSyncConfig result = me;

        if (result == null) {
            synchronized (UserGroupSyncConfig.class) {
                result = me;

                if (result == null) {
                    me     = new UserGroupSyncConfig();
                    result = me;
                }
            }
        }

        return result;
    }

    public static synchronized boolean isUgsyncServiceActive() {
        return UserSyncHAInitializerImpl.getInstance(UserGroupSyncConfig.getInstance().getUserGroupConfig()).isActive();
    }

    public Configuration getConfig() {
        Configuration ret = new Configuration();

        for (String propName : prop.stringPropertyNames()) {
            ret.set(propName, prop.getProperty(propName));
        }

        return ret;
    }

    public Configuration getUserGroupConfig() {
        return userGroupConfig;
    }

    public String getUserSyncFileSource() {
        return prop.getProperty(UGSYNC_SOURCE_FILE_PROC);
    }

    public String getUserSyncFileSourceDelimiter() {
        String val = prop.getProperty(UGSYNC_SOURCE_FILE_DELIMITER);

        if (val == null) {
            val = prop.getProperty(UGSYNC_SOURCE_FILE_DELIMITERER);

            if (val == null) {
                val = DEFAULT_USER_GROUP_TEXTFILE_DELIMITER;
            }
        }

        return val;
    }

    public String getUnixPasswordFile() {
        String val = prop.getProperty(UGSYNC_UNIX_PASSWORD_FILE);

        if (val == null) {
            val = DEFAULT_UGSYNC_UNIX_PASSWORD_FILE;
        }

        return val;
    }

    public String getUnixGroupFile() {
        String val = prop.getProperty(UGSYNC_UNIX_GROUP_FILE);

        if (val == null) {
            val = DEFAULT_UGSYNC_UNIX_GROUP_FILE;
        }

        return val;
    }

    public String getUnixBackend() {
        String val = prop.getProperty(UGSYNC_UNIX_BACKEND);

        if (val == null) {
            val = DEFAULT_UGSYNC_UNIX_BACKEND;
        }

        return val;
    }

    public boolean isUserSyncEnabled() {
        String val = prop.getProperty(UGSYNC_ENABLED_PROP);

        return (val != null && val.trim().equalsIgnoreCase("true"));
    }

    public String getEnumerateGroups() {
        return prop.getProperty(UGSYNC_GROUP_ENUMERATE_GROUPS);
    }

    public boolean isGroupEnumerateEnabled() {
        String val = prop.getProperty(UGSYNC_GROUP_ENUMERATE_ENABLED);

        return (val != null && val.trim().equalsIgnoreCase("true"));
    }

    public boolean isMockRunEnabled() {
        String val = prop.getProperty(UGSYNC_MOCK_RUN_PROP);

        return (val != null && val.trim().equalsIgnoreCase("true"));
    }

    public boolean isTestRunEnabled() {
        String val = prop.getProperty(UGSYNC_TEST_RUN_PROP);

        return (val != null && val.trim().equalsIgnoreCase("true"));
    }

    public String getPolicyManagerBaseURL() {
        return prop.getProperty(UGSYNC_PM_URL_PROP);
    }

    public String getMinUserId() {
        return prop.getProperty(UGSYNC_MIN_USERID_PROP);
    }

    public String getMinGroupId() {
        String mgid = prop.getProperty(UGSYNC_MIN_GROUPID_PROP);

        if (mgid == null) {
            mgid = DEFAULT_UGSYNC_MIN_GROUPID;
        }

        return mgid;
    }

    public String getMaxRecordsPerAPICall() {
        return prop.getProperty(UGSYNC_MAX_RECORDS_PER_API_CALL_PROP);
    }

    public String getSSLKeyStoreType() {
        return prop.getProperty(SSL_KEYSTORE_FILE_TYPE_PARAM, KeyStore.getDefaultType());
    }

    public String getSSLTrustStoreType() {
        return prop.getProperty(SSL_TRUSTSTORE_FILE_TYPE_PARAM, KeyStore.getDefaultType());
    }

    public String getSSLKeyStorePath() {
        return prop.getProperty(SSL_KEYSTORE_PATH_PARAM);
    }

    public String getSSLKeyStorePathPassword() {
        if (prop.containsKey(LGSYNC_LDAP_BIND_KEYSTORE)) {
            String path = prop.getProperty(LGSYNC_LDAP_BIND_KEYSTORE);

            if (path != null) {
                if (!path.trim().isEmpty()) {
                    if ("bcfks".equalsIgnoreCase(getSSLKeyStoreType())) {
                        String crendentialProviderPrefixBcfks = "bcfks://file";

                        path = crendentialProviderPrefixBcfks + path;
                    }

                    String password = CredentialReader.getDecryptedString(path.trim(), SSL_KEYSTORE_PATH_PASSWORD_ALIAS.trim(), getSSLKeyStoreType());

                    if (password != null && !password.trim().isEmpty() && !"none".equalsIgnoreCase(password.trim()) && !"_".equalsIgnoreCase(password.trim())) {
                        prop.setProperty(SSL_KEYSTORE_PATH_PASSWORD_PARAM, password);
                    }
                }
            }
        }

        return prop.getProperty(SSL_KEYSTORE_PATH_PASSWORD_PARAM);
    }

    public String getSSLTrustStorePath() {
        return prop.getProperty(SSL_TRUSTSTORE_PATH_PARAM);
    }

    public String getSSLTrustStorePathPassword() {
        if (prop.containsKey(LGSYNC_LDAP_BIND_KEYSTORE)) {
            String path = prop.getProperty(LGSYNC_LDAP_BIND_KEYSTORE);

            if (path != null) {
                if (!path.trim().isEmpty()) {
                    if ("bcfks".equalsIgnoreCase(getSSLKeyStoreType())) {
                        String crendentialProviderPrefixBcfks = "bcfks://file";

                        path = crendentialProviderPrefixBcfks + path;
                    }

                    String password = CredentialReader.getDecryptedString(path.trim(), SSL_TRUSTSTORE_PATH_PASSWORD_ALIAS, getSSLKeyStoreType());

                    if (password != null && !password.trim().isEmpty() && !"none".equalsIgnoreCase(password.trim()) && !"_".equalsIgnoreCase(password.trim())) {
                        prop.setProperty(SSL_TRUSTSTORE_PATH_PASSWORD_PARAM, password);
                    }
                }
            }
        }

        return prop.getProperty(SSL_TRUSTSTORE_PATH_PASSWORD_PARAM);
    }

    public long getUpdateMillisMin() {
        String val = prop.getProperty(UGSYNC_UPDATE_MILLIS_MIN);

        if (val == null) {
            return DEFAULT_UGSYNC_UPDATE_MILLIS_MIN;
        }

        long ret = Long.parseLong(val);

        return Math.max(ret, DEFAULT_UGSYNC_UPDATE_MILLIS_MIN);
    }

    public long getInitSleepTimeInMillisBetweenCycle() throws Throwable {
        long          initSleepValue;
        Configuration config = getUserGroupConfig();

        if (config.getBoolean(UGSYNC_SERVER_HA_ENABLED_PARAM, false)) {
            initSleepValue = UGSYNC_INIT_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_MIN_VALUE_FOR_HA;
        } else {
            initSleepValue = getSleepTimeInMillisBetweenCycle();
        }

        return initSleepValue;
    }

    public long getSleepTimeInMillisBetweenCycle() throws Throwable {
        String  val         = prop.getProperty(UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_PARAM);
        boolean isLdapForce = Boolean.parseBoolean(prop.getProperty(UGSYNC_SLEEP_LDAP_FORCE_TIME_IN_MILLIS_BETWEEN_CYCLE_PARAM_ENABLED));
        String  className   = getUserGroupSource().getClass().getName();

        if (val == null) {
            if (LGSYNC_SOURCE_CLASS.equals(className)) {
                return UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_LDAP_DEFAULT_VALUE;
            } else {
                return UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_UNIX_DEFAULT_VALUE;
            }
        } else {
            long ret = Long.parseLong(val);
            long minInterval;

            if (LGSYNC_SOURCE_CLASS.equals(className)) {
                if (isLdapForce && ret < UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_LDAP_DEFAULT_VALUE) {
                    minInterval = ret;

                    LOG.info("If you force the synchronization time of ldap users to be less than the default of 3600s, this setting [{}] millisecond will take effect", minInterval);
                } else {
                    minInterval = UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_LDAP_DEFAULT_VALUE;
                }
            } else if (UGSYNC_SOURCE_CLASS.equals(className)) {
                minInterval = UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_UNIX_DEFAULT_VALUE;
            } else {
                minInterval = UGSYNC_SLEEP_TIME_IN_MILLIS_BETWEEN_CYCLE_MIN_VALUE;
            }

            if ((!isTestRunEnabled()) && (ret < minInterval)) {
                LOG.info("Sleep Time Between Cycle can not be lower than [{}] milli sec. resetting to min value.", minInterval);

                ret = minInterval;
            }

            return ret;
        }
    }

    public UserGroupSource getUserGroupSource() throws Throwable {
        String                 className     = getUserGroupSourceClassName();
        Class<UserGroupSource> ugSourceClass = (Class<UserGroupSource>) Class.forName(className);

        return ugSourceClass.newInstance();
    }

    public UserGroupSink getUserGroupSink() throws Throwable {
        String val = prop.getProperty(UGSYNC_SINK_CLASS_PARAM);

        if (val == null || val.trim().isEmpty()) {
            val = UGSYNC_SINK_CLASS;
        }

        Class<UserGroupSink> ugSinkClass = (Class<UserGroupSink>) Class.forName(val);

        return ugSinkClass.newInstance();
    }

    public String getLdapUrl() throws Throwable {
        String val = prop.getProperty(LGSYNC_LDAP_URL);

        if (val == null || val.trim().isEmpty()) {
            throw new Exception(LGSYNC_LDAP_URL + " for LdapGroupSync is not specified");
        }

        return val;
    }

    public String getLdapBindDn() throws Throwable {
        String val = prop.getProperty(LGSYNC_LDAP_BIND_DN);

        if (val == null || val.trim().isEmpty()) {
            throw new Exception(LGSYNC_LDAP_BIND_DN + " for LdapGroupSync is not specified");
        }

        return val;
    }

    public String getLdapBindPassword() {
        //update credential from keystore
        if (prop.containsKey(LGSYNC_LDAP_BIND_KEYSTORE)) {
            String path = prop.getProperty(LGSYNC_LDAP_BIND_KEYSTORE);

            if (path != null) {
                if (!path.trim().isEmpty()) {
                    if ("bcfks".equalsIgnoreCase(getSSLKeyStoreType())) {
                        String crendentialProviderPrefixBcfks = "bcfks://file";

                        path = crendentialProviderPrefixBcfks + path;
                    }

                    String password = CredentialReader.getDecryptedString(path.trim(), LGSYNC_LDAP_BIND_ALIAS, getSSLKeyStoreType());

                    if (password != null && !password.trim().isEmpty() && !password.trim().equalsIgnoreCase("none")) {
                        prop.setProperty(LGSYNC_LDAP_BIND_PASSWORD, password);
                    }
                }
            }
        }

        return prop.getProperty(LGSYNC_LDAP_BIND_PASSWORD);
    }

    public String getLdapAuthenticationMechanism() {
        String val = prop.getProperty(LGSYNC_LDAP_AUTHENTICATION_MECHANISM);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_AUTHENTICATION_MECHANISM;
        }

        return val;
    }

    public String getUserSearchBase() throws Throwable {
        String val = prop.getProperty(LGSYNC_USER_SEARCH_BASE);

        if (val == null || val.trim().isEmpty()) {
            val = getSearchBase();
        }

        if (val == null || val.trim().isEmpty()) {
            throw new Exception(LGSYNC_USER_SEARCH_BASE + " for LdapGroupSync is not specified");
        }

        return val;
    }

    /* Used only for unit testing */
    public void setUserSearchBase(String userSearchBase) {
        prop.setProperty(LGSYNC_USER_SEARCH_BASE, userSearchBase);
    }

    public int getUserSearchScope() {
        String val = prop.getProperty(LGSYNC_USER_SEARCH_SCOPE);

        if (val == null || val.trim().isEmpty()) {
            return 2; //subtree scope
        }

        val = val.trim().toLowerCase();

        if (val.equals("0") || val.startsWith("base")) {
            return 0; // object scope
        } else if (val.equals("1") || val.startsWith("one")) {
            return 1; // one level scope
        } else {
            return 2; // subtree scope
        }
    }

    public String getUserObjectClass() {
        String val = prop.getProperty(LGSYNC_USER_OBJECT_CLASS);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_USER_OBJECT_CLASS;
        }

        return val;
    }

    /* Used only for unit testing */
    public void setUserObjectClass(String userObjectClass) {
        prop.setProperty(LGSYNC_USER_OBJECT_CLASS, userObjectClass);
    }

    public String getUserSearchFilter() {
        return prop.getProperty(LGSYNC_USER_SEARCH_FILTER);
    }

    /* Used only for unit testing */
    public void setUserSearchFilter(String filter) {
        prop.setProperty(LGSYNC_USER_SEARCH_FILTER, filter);
    }

    public String getUserNameAttribute() {
        String val = prop.getProperty(LGSYNC_USER_NAME_ATTRIBUTE);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_USER_NAME_ATTRIBUTE;
        }

        return val;
    }

    /* Used only for unit testing */
    public void setUserNameAttribute(String userNameAttr) {
        prop.setProperty(LGSYNC_USER_NAME_ATTRIBUTE, userNameAttr);
    }

    public String getUserGroupNameAttribute() {
        String val = prop.getProperty(LGSYNC_USER_GROUP_NAME_ATTRIBUTE);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_USER_GROUP_NAME_ATTRIBUTE;
        }

        return val;
    }

    public String getGroupNames() {
        return prop.getProperty(LGSYNC_GROUPNAMES);
    }

    public Set<String> getGroupNameSet() {
        String      groupNames    = getGroupNames();
        Set<String> groupNamegSet = new HashSet<>();

        if (StringUtils.isNotEmpty(groupNames)) {
            StringTokenizer st = new StringTokenizer(groupNames, ";");

            while (st.hasMoreTokens()) {
                groupNamegSet.add(st.nextToken().trim().toLowerCase());
            }
        }

        return groupNamegSet;
    }

    public Set<String> getUserGroupNameAttributeSet() {
        String          uga                       = getUserGroupNameAttribute();
        StringTokenizer st                        = new StringTokenizer(uga, ",");
        Set<String>     userGroupNameAttributeSet = new HashSet<>();

        while (st.hasMoreTokens()) {
            userGroupNameAttributeSet.add(st.nextToken().trim());
        }

        return userGroupNameAttributeSet;
    }

    public Set<String> getOtherUserAttributes() {
        String otherAttributes = prop.getProperty(LGSYNC_OTHER_USER_ATTRIBUTES);

        if (otherAttributes == null || otherAttributes.trim().isEmpty()) {
            otherAttributes = DEFAULT_OTHER_USER_ATTRIBUTES;
        }

        StringTokenizer st                  = new StringTokenizer(otherAttributes, ",");
        Set<String>     otherUserAttributes = new HashSet<>();

        while (st.hasMoreTokens()) {
            otherUserAttributes.add(st.nextToken().trim());
        }

        return otherUserAttributes;
    }

    public String getUserCloudIdAttribute() {
        String val = prop.getProperty(LGSYNC_USER_CLOUDID_ATTRIBUTE);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_USER_CLOUDID_ATTRIBUTE;
        }

        return val;
    }

    public String getUserCloudIdAttributeDataType() {
        String val = prop.getProperty(LGSYNC_USER_CLOUDID_ATTRIBUTE_DATATYPE);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_USER_CLOUDID_ATTRIBUTE_DATATYPE;
        }

        return val;
    }

    public String getOtherUserAttributeDataType(String attrName) {
        String attrType = prop.getProperty(LGSYNC_OTHER_USER_ATTRIBUTES + "." + attrName + "datatype");

        if (attrType == null || attrType.isEmpty()) {
            attrType = "String";
        }

        return attrType.trim();
    }

    public String getUserNameCaseConversion() {
        String ret = prop.getProperty(UgsyncCommonConstants.UGSYNC_USERNAME_CASE_CONVERSION_PARAM, UgsyncCommonConstants.DEFAULT_UGSYNC_USERNAME_CASE_CONVERSION_VALUE);

        return ret.trim().toLowerCase();
    }

    public String getGroupNameCaseConversion() {
        String ret = prop.getProperty(UgsyncCommonConstants.UGSYNC_GROUPNAME_CASE_CONVERSION_PARAM, UgsyncCommonConstants.DEFAULT_UGSYNC_GROUPNAME_CASE_CONVERSION_VALUE);

        return ret.trim().toLowerCase();
    }

    public String getSearchBase() {
        return prop.getProperty(LGSYNC_SEARCH_BASE);
    }

    public boolean isPagedResultsEnabled() {
        boolean pagedResultsEnabled;
        String  val = prop.getProperty(LGSYNC_PAGED_RESULTS_ENABLED);

        if (val == null || val.trim().isEmpty()) {
            pagedResultsEnabled = DEFAULT_LGSYNC_PAGED_RESULTS_ENABLED;
        } else {
            pagedResultsEnabled = Boolean.parseBoolean(val);
        }

        return pagedResultsEnabled;
    }

    /* Used only for unit testing */
    public void setPagedResultsEnabled(boolean pagedResultsEnabled) {
        prop.setProperty(LGSYNC_PAGED_RESULTS_ENABLED, String.valueOf(pagedResultsEnabled));
    }

    public int getPagedResultsSize() {
        int    pagedResultsSize;
        String val = prop.getProperty(LGSYNC_PAGED_RESULTS_SIZE);

        if (val == null || val.trim().isEmpty()) {
            pagedResultsSize = DEFAULT_LGSYNC_PAGED_RESULTS_SIZE;
        } else {
            pagedResultsSize = Integer.parseInt(val);
        }

        if (pagedResultsSize < 1) {
            pagedResultsSize = DEFAULT_LGSYNC_PAGED_RESULTS_SIZE;
        }

        return pagedResultsSize;
    }

    public boolean isDeltaSyncEnabled() {
        String val = prop.getProperty(LGSYNC_LDAP_DELTASYNC_ENABLED);

        return StringUtils.isBlank(val) ? DEFAULT_LGSYNC_LDAP_DELTASYNC_ENABLED : Boolean.parseBoolean(val);
    }

    public boolean isGroupSearchEnabled() {
        boolean groupSearchEnabled;
        String  val = prop.getProperty(LGSYNC_GROUP_SEARCH_ENABLED);

        if (val == null || val.trim().isEmpty()) {
            groupSearchEnabled = DEFAULT_LGSYNC_GROUP_SEARCH_ENABLED;
        } else {
            groupSearchEnabled = Boolean.parseBoolean(val);
        }

        return groupSearchEnabled;
    }

    /* Used only for unit testing */
    public void setGroupSearchEnabled(boolean groupSearchEnabled) {
        prop.setProperty(LGSYNC_GROUP_SEARCH_ENABLED, String.valueOf(groupSearchEnabled));
    }

    public boolean isGroupSearchFirstEnabled() {
        boolean groupSearchFirstEnabled;
        String  val = prop.getProperty(LGSYNC_GROUP_SEARCH_FIRST_ENABLED);

        if (val == null || val.trim().isEmpty()) {
            groupSearchFirstEnabled = DEFAULT_LGSYNC_GROUP_SEARCH_FIRST_ENABLED;
        } else {
            groupSearchFirstEnabled = Boolean.parseBoolean(val);
        }

        if (!isGroupSearchEnabled()) {
            groupSearchFirstEnabled = false;
        }

        return groupSearchFirstEnabled;
    }

    /* Used only for unit testing */
    public void setGroupSearchFirstEnabled(boolean groupSearchFirstEnabled) {
        prop.setProperty(LGSYNC_GROUP_SEARCH_FIRST_ENABLED, String.valueOf(groupSearchFirstEnabled));
    }

    public boolean isUserSearchEnabled() {
        boolean userSearchEnabled;
        String  val = prop.getProperty(LGSYNC_USER_SEARCH_ENABLED);

        if (val == null || val.trim().isEmpty()) {
            userSearchEnabled = DEFAULT_LGSYNC_USER_SEARCH_ENABLED;
        } else {
            userSearchEnabled = Boolean.parseBoolean(val);
        }

        if (!isGroupSearchFirstEnabled()) {
            userSearchEnabled = true;
        }

        return userSearchEnabled;
    }

    /* Used only for unit testing */
    public void setUserSearchEnabled(boolean userSearchEnabled) {
        prop.setProperty(LGSYNC_USER_SEARCH_ENABLED, String.valueOf(userSearchEnabled));
    }

    public String getGroupSearchBase() throws Throwable {
        String val = prop.getProperty(LGSYNC_GROUP_SEARCH_BASE);

        if (val == null || val.trim().isEmpty()) {
            val = getSearchBase();
        }

        if (val == null || val.trim().isEmpty()) {
            val = getUserSearchBase();
        }

        return val;
    }

    /* Used only for unit testing */
    public void setGroupSearchBase(String groupSearchBase) {
        prop.setProperty(LGSYNC_GROUP_SEARCH_BASE, groupSearchBase);
    }

    public int getGroupSearchScope() {
        String val = prop.getProperty(LGSYNC_GROUP_SEARCH_SCOPE);

        if (val == null || val.trim().isEmpty()) {
            return 2; //subtree scope
        }

        val = val.trim().toLowerCase();

        if (val.equals("0") || val.startsWith("base")) {
            return 0; // object scope
        } else if (val.equals("1") || val.startsWith("one")) {
            return 1; // one level scope
        } else {
            return 2; // subtree scope
        }
    }

    public String getGroupObjectClass() {
        String val = prop.getProperty(LGSYNC_GROUP_OBJECT_CLASS);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_LGSYNC_GROUP_OBJECT_CLASS;
        }

        return val;
    }

    /* Used only for unit testing */
    public void setGroupObjectClass(String groupObjectClass) {
        prop.setProperty(LGSYNC_GROUP_OBJECT_CLASS, groupObjectClass);
    }

    public String getGroupSearchFilter() {
        return prop.getProperty(LGSYNC_GROUP_SEARCH_FILTER);
    }

    /* Used only for unit testing */
    public void setGroupSearchFilter(String filter) {
        prop.setProperty(LGSYNC_GROUP_SEARCH_FILTER, filter);
    }

    public String getUserGroupMemberAttributeName() {
        String val = prop.getProperty(LGSYNC_GROUP_MEMBER_ATTRIBUTE_NAME);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_LGSYNC_GROUP_MEMBER_ATTRIBUTE_NAME;
        }

        return val;
    }

    /* Used only for unit testing */
    public void setUserGroupMemberAttributeName(String groupMemberAttrName) {
        prop.setProperty(LGSYNC_GROUP_MEMBER_ATTRIBUTE_NAME, groupMemberAttrName);
    }

    public String getGroupNameAttribute() {
        String val = prop.getProperty(LGSYNC_GROUP_NAME_ATTRIBUTE);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_LGSYNC_GROUP_NAME_ATTRIBUTE;
        }

        return val;
    }

    public String getGroupCloudIdAttribute() {
        String val = prop.getProperty(LGSYNC_GROUP_CLOUDID_ATTRIBUTE);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_GROUP_CLOUDID_ATTRIBUTE;
        }

        return val;
    }

    public String getGroupCloudIdAttributeDataType() {
        String val = prop.getProperty(LGSYNC_GROUP_CLOUDID_ATTRIBUTE_DATATYPE);

        if (val == null || val.trim().isEmpty()) {
            return DEFAULT_GROUP_CLOUDID_ATTRIBUTE_DATATYPE;
        }

        return val;
    }

    public Set<String> getOtherGroupAttributes() {
        String otherAttributes = prop.getProperty(LGSYNC_OTHER_GROUP_ATTRIBUTES);

        if (otherAttributes == null || otherAttributes.trim().isEmpty()) {
            otherAttributes = DEFAULT_OTHER_GROUP_ATTRIBUTES;
        }

        StringTokenizer st                   = new StringTokenizer(otherAttributes, ",");
        Set<String>     otherGroupAttributes = new HashSet<>();

        while (st.hasMoreTokens()) {
            otherGroupAttributes.add(st.nextToken().trim());
        }

        return otherGroupAttributes;
    }

    public String getOtherGroupAttributeDataType(String attrName) {
        String attrType = prop.getProperty(LGSYNC_OTHER_GROUP_ATTRIBUTES + "." + attrName + "datatype");

        if (attrType == null || attrType.isEmpty()) {
            attrType = "String";
        }

        return attrType.trim();
    }

    public int getGroupHierarchyLevels() {
        int    groupHierarchyLevels;
        String val = prop.getProperty(LGSYNC_GROUP_HIERARCHY_LEVELS);

        if (val == null || val.trim().isEmpty()) {
            groupHierarchyLevels = DEFAULT_LGSYNC_GROUP_HIERARCHY_LEVELS;
        } else {
            groupHierarchyLevels = Integer.parseInt(val);
        }

        if (groupHierarchyLevels < 0) {
            groupHierarchyLevels = DEFAULT_LGSYNC_GROUP_HIERARCHY_LEVELS;
        }

        return groupHierarchyLevels;
    }

    public String getProperty(String aPropertyName) {
        return prop.getProperty(aPropertyName);
    }

    public String getProperty(String aPropertyName, String aDefaultValue) {
        return prop.getProperty(aPropertyName, aDefaultValue);
    }

    public String getPolicyMgrPassword() {
        //update credential from keystore
        String password;

        if (prop.containsKey(SYNC_POLICY_MGR_KEYSTORE)) {
            password = prop.getProperty(SYNC_POLICY_MGR_PASSWORD);

            if (password != null && !password.isEmpty()) {
                return password;
            }
        }

        if (prop.containsKey(SYNC_POLICY_MGR_KEYSTORE) && prop.containsKey(SYNC_POLICY_MGR_ALIAS)) {
            String path  = prop.getProperty(SYNC_POLICY_MGR_KEYSTORE);
            String alias = prop.getProperty(SYNC_POLICY_MGR_ALIAS, "policymgr.user.password");

            if (path != null && alias != null) {
                if (!path.trim().isEmpty() && !alias.trim().isEmpty()) {
                    if ("bcfks".equalsIgnoreCase(getSSLKeyStoreType())) {
                        String crendentialProviderPrefixBcfks = "bcfks" + "://file";

                        path = crendentialProviderPrefixBcfks + path;
                    }

                    try {
                        password = CredentialReader.getDecryptedString(path.trim(), alias.trim(), getSSLKeyStoreType());
                    } catch (Exception ex) {
                        password = null;
                    }

                    if (password != null && !password.trim().isEmpty() && !password.trim().equalsIgnoreCase("none")) {
                        prop.setProperty(SYNC_POLICY_MGR_PASSWORD, password);

                        return password;
                    }
                }
            }
        }

        return null;
    }

    public String getPolicyMgrUserName() {
        String userName = null;

        if (prop.containsKey(SYNC_POLICY_MGR_USERNAME)) {
            userName = prop.getProperty(SYNC_POLICY_MGR_USERNAME);
        }

        if (userName == null || userName.isEmpty()) {
            userName = DEFAULT_POLICYMGR_USERNAME;
        }

        return userName;
    }

    public int getPolicyMgrMaxRetryAttempts() {
        return getIntProperty(prop, SYNC_POLICY_MGR_MAX_RETRY_ATTEMPTS, 0);
    }

    public int getPolicyMgrRetryIntervalMs() {
        return getIntProperty(prop, SYNC_POLICY_MGR_RETRY_INTERVAL_MS, 1_000);
    }

    public String getSyncSource() {
        String syncSource = null;

        if (prop.containsKey(SYNC_SOURCE)) {
            syncSource = prop.getProperty(SYNC_SOURCE);

            if (syncSource == null || syncSource.trim().isEmpty()) {
                syncSource = null;
            } else {
                syncSource = syncSource.trim();
            }
        }

        return syncSource;
    }

    public String getContextReferral() {
        String referral = "ignore";

        if (prop.containsKey(LGSYNC_REFERRAL)) {
            referral = prop.getProperty(LGSYNC_REFERRAL);

            if (referral == null || referral.trim().isEmpty()) {
                referral = DEFAULT_LGSYNC_REFERRAL;
            } else {
                referral = referral.trim().toLowerCase();
            }
        }

        return referral;
    }

    public List<String> getAllRegexPatterns(String baseProperty) {
        List<String> regexPatterns = new ArrayList<>();
        String       baseRegex     = prop.getProperty(baseProperty);

        if (baseRegex == null) {
            return regexPatterns;
        }

        regexPatterns.add(baseRegex);

        int    i         = 1;
        String nextRegex = prop.getProperty(baseProperty + "." + i);

        while (nextRegex != null) {
            regexPatterns.add(nextRegex);

            i++;

            nextRegex = prop.getProperty(baseProperty + "." + i);
        }

        return regexPatterns;
    }

    public String getUserSyncMappingUserNameHandler() {
        String val = prop.getProperty(UgsyncCommonConstants.SYNC_MAPPING_USERNAME_HANDLER);

        if (val == null) {
            val = UgsyncCommonConstants.DEFAULT_SYNC_MAPPING_USERNAME_HANDLER;
        }

        return val;
    }

    public String getUserSyncMappingGroupNameHandler() {
        String val = prop.getProperty(UgsyncCommonConstants.SYNC_MAPPING_GROUPNAME_HANDLER);

        if (val == null) {
            val = UgsyncCommonConstants.DEFAULT_SYNC_MAPPING_GROUPNAME_HANDLER;
        }

        return val;
    }

    public String getGroupRoleRules() {
        if (prop.containsKey(GROUP_BASED_ROLE_ASSIGNMENT_RULES)) {
            String groupRoleRules = prop.getProperty(GROUP_BASED_ROLE_ASSIGNMENT_RULES);

            if (StringUtils.isNotBlank(groupRoleRules)) {
                return groupRoleRules.trim();
            }
        }

        return null;
    }

    public String getWhileListUserRoleRules() {
        if (prop.containsKey(WHITELIST_USER_ROLE_ASSIGNMENT_RULES)) {
            String whiteListUserRoleRules = prop.getProperty(WHITELIST_USER_ROLE_ASSIGNMENT_RULES);

            if (StringUtils.isNotBlank(whiteListUserRoleRules)) {
                return whiteListUserRoleRules.trim();
            }
        }

        return DEFAULT_WHITELIST_USER_ROLE_ASSIGNMENT_RULES;
    }

    public String getUserGroupDelimiter() {
        if (prop.containsKey(USERS_GROUPS_ASSIGNMENT_LIST_DELIMITER)) {
            String userGroupDelimiter = prop.getProperty(USERS_GROUPS_ASSIGNMENT_LIST_DELIMITER);

            if (userGroupDelimiter != null && !userGroupDelimiter.isEmpty()) {
                return userGroupDelimiter;
            }
        }

        return null;
    }

    public String getUserGroupNameDelimiter() {
        if (prop.containsKey(USERNAME_GROUPNAME_ASSIGNMENT_LIST_DELIMITER)) {
            String userGroupNameDelimiter = prop.getProperty(USERNAME_GROUPNAME_ASSIGNMENT_LIST_DELIMITER);

            if (userGroupNameDelimiter != null && !userGroupNameDelimiter.isEmpty()) {
                return userGroupNameDelimiter;
            }
        }

        return null;
    }

    public boolean isUserSyncRangerCookieEnabled() {
        String val = prop.getProperty(USERSYNC_RANGER_COOKIE_ENABLED_PROP);

        return val == null || Boolean.parseBoolean(val.trim());
    }

    public String getRangerAdminCookieName() {
        String ret = RangerCommonConstants.DEFAULT_COOKIE_NAME;
        String val = prop.getProperty(RANGER_ADMIN_COOKIE_NAME_PROPS);

        if (StringUtils.isNotBlank(val)) {
            ret = val;
        }

        return ret;
    }

    public String getRoleDelimiter() {
        if (prop.containsKey(ROLE_ASSIGNMENT_LIST_DELIMITER)) {
            String roleDelimiter = prop.getProperty(ROLE_ASSIGNMENT_LIST_DELIMITER);

            if (roleDelimiter != null && !roleDelimiter.isEmpty()) {
                return roleDelimiter;
            }
        }

        return null;
    }

    public boolean isStartTlsEnabled() {
        boolean starttlsEnabled;
        String  val = prop.getProperty(LGSYNC_LDAP_STARTTLS_ENABLED);

        if (val == null || val.trim().isEmpty()) {
            starttlsEnabled = DEFAULT_LGSYNC_LDAP_STARTTLS_ENABLED;
        } else {
            starttlsEnabled = Boolean.parseBoolean(val);
        }

        return starttlsEnabled;
    }

    /* Used only for unit testing */
    public void setProperty(String name, String value) {
        prop.setProperty(name, value);
    }

    /* Used only for unit testing */
    public void setDeltaSync(boolean deltaSyncEnabled) {
        prop.setProperty(LGSYNC_LDAP_DELTASYNC_ENABLED, String.valueOf(deltaSyncEnabled));
    }

    /* Used only for unit testing */
    public void setGroupHierarchyLevel(int groupHierarchyLevel) {
        prop.setProperty(LGSYNC_GROUP_HIERARCHY_LEVELS, String.valueOf(groupHierarchyLevel));
    }

    /* Used only for unit testing */
    public void setGroupnames(String groupnames) {
        prop.setProperty(LGSYNC_GROUPNAMES, groupnames);
    }

    public String getUserSyncMetricsFileName() throws IOException {
        String val = prop.getProperty(UGSYNC_METRICS_FILEPATH);

        if (StringUtils.isBlank(val)) {
            if (StringUtils.isBlank(prop.getProperty("ranger.usersync.logdir"))) {
                if (StringUtils.isBlank(System.getProperty("logdir"))) {
                    val = DEFAULT_UGSYNC_METRICS_FILEPATH;
                } else {
                    val = System.getProperty("logdir");
                }
            } else {
                val = prop.getProperty("ranger.usersync.logdir");
            }
        }

        if (Files.notExists(Paths.get(val))) {
            String current = new File(".").getCanonicalPath();

            val = current + "/" + val;

            if (Files.notExists(Paths.get(val))) {
                return null;
            }
        }

        StringBuilder pathAndFileName = new StringBuilder(val);

        if (!val.endsWith("/")) {
            pathAndFileName.append("/");
        }

        String fileName = prop.getProperty(UGSYNC_METRICS_FILENAME);

        if (StringUtils.isBlank(fileName)) {
            fileName = DEFAULT_UGSYNC_METRICS_FILENAME;
        }

        pathAndFileName.append(fileName);

        return pathAndFileName.toString();
    }

    public long getUserSyncMetricsFrequency() {
        long   ret = DEFAULT_UGSYNC_METRICS_FREQUENCY_TIME_IN_MILLIS;
        String val = prop.getProperty(UGSYNC_METRICS_FREQUENCY_TIME_IN_MILLIS_PARAM);

        if (StringUtils.isNotBlank(val)) {
            try {
                ret = Long.parseLong(val);
            } catch (NumberFormatException exception) {
                // Ignore
            }
        }

        return ret;
    }

    public boolean isUserSyncMetricsEnabled() {
        String val = prop.getProperty(UGSYNC_METRICS_ENABLED_PROP);

        return "true".equalsIgnoreCase(StringUtils.trimToEmpty(val));
    }

    public boolean isUserSyncDeletesEnabled() {
        boolean isUserSyncDeletesEnabled;
        String  val = prop.getProperty(UGSYNC_DELETES_ENABLED);

        if (StringUtils.isEmpty(val)) {
            isUserSyncDeletesEnabled = DEFAULT_UGSYNC_DELETES_ENABLED;
        } else {
            isUserSyncDeletesEnabled = Boolean.parseBoolean(val);
        }

        return isUserSyncDeletesEnabled;
    }

    /*
     * This is the frequency of computing deleted users/groups from the sync source.
     * Default and minimum value is 8hrs
     * If the delete frequency interval value is less than sync interval and greater than 8hrs,
     * then deleted objects are computed at every sync cycle.
     */
    public long getUserSyncDeletesFrequency() {
        long   ret = 1;
        String val = prop.getProperty(UGSYNC_DELETES_FREQUENCY);

        if (StringUtils.isNotBlank(val)) {
            ret = Long.parseLong(val);

            if (!isTestRunEnabled() && ret < DEFAULT_UGSYNC_DELETES_FREQUENCY) {
                LOG.info("Frequency of computing deletes cannot be set below " + DEFAULT_UGSYNC_DELETES_FREQUENCY);

                ret = DEFAULT_UGSYNC_DELETES_FREQUENCY;
            }
        }

        return ret;
    }

    public String getCurrentSyncSource() throws Throwable {
        String currentSyncSource;
        String className = getUserGroupSource().getClass().getName();

        if (LGSYNC_SOURCE_CLASS.equals(className)) {
            currentSyncSource = "LDAP/AD";
        } else if (UGSYNC_SOURCE_CLASS.equalsIgnoreCase(className)) {
            currentSyncSource = "Unix";
        } else {
            currentSyncSource = "File";
        }

        return currentSyncSource;
    }

    public boolean isUserSyncNameValidationEnabled() {
        boolean isUserSyncNameValidationEnabled;
        String  val = prop.getProperty(UGSYNC_NAME_VALIDATION_ENABLED);

        if (StringUtils.isEmpty(val)) {
            isUserSyncNameValidationEnabled = DEFAULT_UGSYNC_NAME_VALIDATION_ENABLED;
        } else {
            isUserSyncNameValidationEnabled = Boolean.parseBoolean(val);
        }

        return isUserSyncNameValidationEnabled;
    }

    public String getRegexSeparator() {
        String ret = UgsyncCommonConstants.DEFAULT_MAPPING_SEPARATOR;
        String val = prop.getProperty(UgsyncCommonConstants.SYNC_MAPPING_SEPARATOR);

        if (StringUtils.isNotEmpty(val)) {
            if (val.length() == 1) {
                ret = val;
            } else {
                LOG.warn("More than one character found in RegEx Separator, using default RegEx Separator /");
            }
        }

        LOG.info("Using {} as the RegEx Separator", ret);

        return ret;
    }

    public boolean isSyncSourceValidationEnabled() {
        boolean isSyncSourceValidationEnabled = DEFAULT_UGSYNC_SYNC_SOURCE_VALIDATION_ENABLED;
        String  val                           = prop.getProperty(UGSYNC_SYNC_SOURCE_VALIDATION_ENABLED);

        if (StringUtils.isNotEmpty(val)) {
            isSyncSourceValidationEnabled = Boolean.parseBoolean(val);
        }

        return isSyncSourceValidationEnabled;
    }

    private void init() {
        XMLUtils.loadConfig(DEFAULT_CONFIG_FILE, prop);
        XMLUtils.loadConfig(CORE_SITE_CONFIG_FILE, prop);
        XMLUtils.loadConfig(CONFIG_FILE, prop);

        userGroupConfig = getConfig();
    }

    private String getUserGroupSourceClassName() {
        String val = prop.getProperty(UGSYNC_SOURCE_CLASS_PARAM);
        String className;

        String syncSource;

        if (val == null || val.trim().isEmpty()) {
            syncSource = getSyncSource();
        } else {
            if (val.equalsIgnoreCase(LGSYNC_SOURCE_CLASS)) {
                val = LGSYNC_SOURCE_CLASS;
            }

            syncSource = val;
        }

        className = val;

        if (syncSource != null && syncSource.equalsIgnoreCase("UNIX")) {
            className = UGSYNC_SOURCE_CLASS;
        } else if (syncSource != null && syncSource.equalsIgnoreCase("LDAP")) {
            className = LGSYNC_SOURCE_CLASS;
        }

        return className;
    }

    private int getIntProperty(Properties prop, String key, int defaultValue) {
        int    ret = defaultValue;
        String val = prop.getProperty(key);

        if (StringUtils.isNotBlank(val)) {
            try {
                ret = Integer.parseInt(val);
            } catch (NumberFormatException exp) {
                LOG.warn("Invalid value for property: {} = {} , will use default value: {}", key, val, defaultValue, exp);
            }
        }

        return ret;
    }
}
