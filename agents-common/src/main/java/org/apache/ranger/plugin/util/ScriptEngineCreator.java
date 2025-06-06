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

package org.apache.ranger.plugin.util;

import org.apache.ranger.plugin.classloader.RangerPluginClassLoader;

import javax.script.ScriptEngine;

public interface ScriptEngineCreator {
    ScriptEngine getScriptEngine(ClassLoader clsLoader);

    default ClassLoader getDefaultClassLoader() {
        ClassLoader ret = Thread.currentThread().getContextClassLoader();

        // Most Ranger plugins use a shim layer and RangerPluginClassLoader for isolation of libraries
        // loaded by the plugin implementation. The shim ensures that all calls to the plugin would have
        // RangerPluginClassLoader as current thread classloader.
        // Some plugins, like Trino, use their own isolation mechanism. In these plugins, current thread's
        // classloader may not load libraries in the plugin directory, which can result in failure in
        // creation of the script engine. Using the classloader of current class to resolve this issue.
        return ret instanceof RangerPluginClassLoader ? ret : this.getClass().getClassLoader();
    }
}
