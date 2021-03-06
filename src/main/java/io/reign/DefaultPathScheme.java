/*
 * Copyright 2013 Yen Pai ypai@reign.io
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.reign;

import io.reign.util.JacksonUtil;

import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * Default path scheme.
 * 
 * @author ypai
 * 
 */
public class DefaultPathScheme implements PathScheme {

    private static final Pattern PATTERN_PATH_TOKENIZER = Pattern.compile("/");

    private final String basePath;

    private final String frameworkClusterId;

    public DefaultPathScheme(String basePath, String frameworkClusterId) {
        this.basePath = basePath;
        this.frameworkClusterId = frameworkClusterId;
    }

    @Override
    public String getFrameworkClusterId() {
        return this.frameworkClusterId;
    }

    @Override
    public String getFrameworkBasePath() {
        return basePath;
    }

    @Override
    public String getPathTokenizer() {
        return "/";
    }

    @Override
    public String getAbsolutePath(PathType pathType) {
        return basePath + "/" + pathType;
    }

    @Override
    public String getAbsolutePath(PathType pathType, String relativePath) {
        if (relativePath == null || "".equals(relativePath)) {
            return getAbsolutePath(pathType);
        }
        return basePath + "/" + pathType + "/" + relativePath;
    }

    @Override
    public String getAbsolutePath(PathType pathType, String... pathTokens) {
        return getAbsolutePath(pathType, joinTokens(pathTokens));
    }

    @Override
    public String getParentPath(String path) {
        if (!isValidPath(path)) {
            throw new IllegalArgumentException("Invalid path:  path=" + path);
        }
        if ("/".equals(path)) {
            return null;
        }
        return path.substring(0, path.lastIndexOf("/"));
    }

    @Override
    public String joinPaths(String... paths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];

            if (!isValidPath(path)) {
                throw new IllegalArgumentException("Invalid path:  path=" + path);
            }

            // strip trailing slash
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            // only add slash btw. paths if we have to
            if (i > 0 && !path.startsWith("/")) {
                sb.append('/');
            }

            sb.append(path);
        }

        return sb.toString();

    }

    @Override
    public String[] tokenizePath(String path) {
        path = path.trim();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return PATTERN_PATH_TOKENIZER.split(path);
    }

    @Override
    public String joinTokens(String... pathTokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : pathTokens) {
            if (!isValidToken(token)) {
                throw new IllegalArgumentException("Invalid path token:  pathToken='" + token + "'");

            }
            sb.append(token);
            sb.append('/');
        }
        return sb.substring(0, sb.length() - 1);
    }

    @Override
    public boolean isValidToken(String pathToken) {
        return !StringUtils.isBlank(pathToken) && pathToken.charAt(0) != '/'
                && pathToken.charAt(pathToken.length() - 1) != '/';
    }

    @Override
    public boolean isValidPath(String path) {
        return (!StringUtils.isBlank(path) && !path.endsWith("/")) || "/".equals(path);
    }

    @Override
    public String toPathToken(NodeId nodeId) {
        try {
            String pathToken = JacksonUtil.getObjectMapper().writeValueAsString(nodeId);
            if (isValidToken(pathToken)) {
                return pathToken;
            } else {
                throw new IllegalStateException("Value does not convert to valid path token:  pathToken=" + pathToken);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
