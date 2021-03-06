/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.platform.web.common;

/**
 * Helper class to detect Html5 Dnd compliant browsers based on the User Agent string
 *
 * @author Tiry (tdelprat@nuxeo.com)
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @deprecated since 7.1, use {@link org.nuxeo.common.utils.UserAgentMatcher} instead.
 */
@Deprecated
public class UserAgentMatcher {

    private UserAgentMatcher() {
        // Helper class
    }

    public static boolean isFirefox3(String UA) {
        return org.nuxeo.common.utils.UserAgentMatcher.isFirefox3(UA);
    }

    public static boolean isFirefox4OrMore(String UA) {
        return org.nuxeo.common.utils.UserAgentMatcher.isFirefox4OrMore(UA);
    }

    public static boolean isSafari5(String UA) {
        return org.nuxeo.common.utils.UserAgentMatcher.isSafari5(UA);
    }

    public static boolean isChrome(String UA) {
        return org.nuxeo.common.utils.UserAgentMatcher.isChrome(UA);
    }

    public static boolean html5DndIsSupported(String UA) {
        return org.nuxeo.common.utils.UserAgentMatcher.html5DndIsSupported(UA);
    }

    public static boolean isMSIE6or7(String UA) {
        return org.nuxeo.common.utils.UserAgentMatcher.isMSIE6or7(UA);
    }

    /**
     * @since 5.9.5
     */
    public static boolean isMSIE10OrMore(String UA) {
        return org.nuxeo.common.utils.UserAgentMatcher.isMSIE10OrMore(UA);
    }

    public static boolean isHistoryPushStateSupported(String UA) {
        return org.nuxeo.common.utils.UserAgentMatcher.isHistoryPushStateSupported(UA);
    }
}
