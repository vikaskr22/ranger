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

package org.apache.ranger.plugin.conditionevaluator;

import org.apache.commons.collections.CollectionUtils;
import org.apache.ranger.plugin.contextenricher.RangerTagForEval;
import org.apache.ranger.plugin.policyengine.RangerAccessRequest;
import org.apache.ranger.plugin.util.RangerAccessRequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class RangerTagsAllPresentConditionEvaluator extends RangerAbstractConditionEvaluator {
    private static final Logger LOG = LoggerFactory.getLogger(RangerTagsAllPresentConditionEvaluator.class);

    private final Set<String> policyConditionTags = new HashSet<>();

    @Override
    public void init() {
        LOG.debug("==> RangerTagsAllPresentConditionEvaluator.init({})", condition);

        super.init();

        if (condition != null) {
            for (String value : condition.getValues()) {
                policyConditionTags.add(value.trim());
            }
        }

        LOG.debug("<== RangerTagsAllPresentConditionEvaluator.init({}): Tags[{}]", condition, policyConditionTags);
    }

    @Override
    public boolean isMatched(RangerAccessRequest request) {
        LOG.debug("==> RangerTagsAllPresentConditionEvaluator.isMatched({})", request);

        boolean matched = true;

        if (CollectionUtils.isNotEmpty(policyConditionTags)) {
            Set<RangerTagForEval> resourceTags = RangerAccessRequestUtil.getRequestTagsFromContext(request.getContext());

            // check if resource Tags  atleast have to have all the tags in policy Condition
            if (CollectionUtils.isNotEmpty(resourceTags)) {
                Set<String> tags = new HashSet<>(resourceTags.size());

                for (RangerTagForEval tag : resourceTags) {
                    tags.add(tag.getType());
                }

                matched = tags.containsAll(policyConditionTags);
            } else {
                matched = false;
            }
        }

        LOG.debug("<== RangerTagsAllPresentConditionEvaluator.isMatched({}): {}", request, matched);

        return matched;
    }
}
