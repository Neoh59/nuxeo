/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.core.security;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.Access;
import org.nuxeo.ecm.core.api.security.PermissionProvider;
import org.nuxeo.ecm.core.api.security.PolicyService;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.SecuritySummaryEntry;
import org.nuxeo.ecm.core.api.security.impl.SecuritySummaryEntryImpl;
import org.nuxeo.ecm.core.model.Document;
import org.nuxeo.ecm.core.model.Session;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * @author Bogdan Stefanescu
 * @author Olivier Grisel
 * @author Anahide Tchertchian
 *
 */
// TODO: improve caching invalidation
// TODO: remove "implements SecurityConstants" and check that it doesn't break
// anything
public class SecurityService extends DefaultComponent {

    public static final ComponentName NAME = new ComponentName(
            "org.nuxeo.ecm.core.security.SecurityService");

    public static final String PERMISSIONS_EXTENSION_POINT = "permissions";

    private static final String PERMISSIONS_VISIBILITY_EXTENSION_POINT = "permissionsVisibility";

    private static final String POLICIES_EXTENSION_POINT = "policies";

    private static final Log log = LogFactory.getLog(SecurityService.class);

    private PermissionProviderLocal permissionProvider;

    private SecurityPolicyService securityPolicyService;

    // private SecurityManager securityManager;

    @Override
    public void activate(ComponentContext context) throws Exception {
        super.activate(context);
        permissionProvider = new DefaultPermissionProvider();
        securityPolicyService = new SecurityPolicyServiceImpl();
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        super.deactivate(context);
        permissionProvider = null;
        securityPolicyService = null;
    }

    @Override
    public void registerContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (PERMISSIONS_EXTENSION_POINT.equals(extensionPoint)
                && contribution instanceof PermissionDescriptor) {
            permissionProvider.registerDescriptor((PermissionDescriptor) contribution);
        } else if (PERMISSIONS_VISIBILITY_EXTENSION_POINT.equals(extensionPoint)
                && contribution instanceof PermissionVisibilityDescriptor) {
            permissionProvider.registerDescriptor((PermissionVisibilityDescriptor) contribution);
        } else if (POLICIES_EXTENSION_POINT.equals(extensionPoint)
                && contribution instanceof SecurityPolicyDescriptor) {
            securityPolicyService.registerDescriptor((SecurityPolicyDescriptor) contribution);
        }
    }

    @Override
    public void unregisterContribution(Object contribution,
            String extensionPoint, ComponentInstance contributor)
            throws Exception {
        if (PERMISSIONS_EXTENSION_POINT.equals(extensionPoint)
                && contribution instanceof PermissionDescriptor) {
            permissionProvider.unregisterDescriptor((PermissionDescriptor) contribution);
        } else if (PERMISSIONS_VISIBILITY_EXTENSION_POINT.equals(extensionPoint)
                && contribution instanceof PermissionVisibilityDescriptor) {
            permissionProvider.unregisterDescriptor((PermissionVisibilityDescriptor) contribution);
        } else if (POLICIES_EXTENSION_POINT.equals(extensionPoint)
                && contribution instanceof SecurityPolicyDescriptor) {
            securityPolicyService.unregisterDescriptor((SecurityPolicyDescriptor) contribution);
        }
    }

    public PermissionProvider getPermissionProvider() {
        return permissionProvider;
    }

    public void invalidateCache(Session session, String username) {
        session.getRepository().getSecurityManager().invalidateCache(session);
    }

    public boolean checkPermission(Document doc, Principal principal,
            String permission) throws SecurityException {
        String username = principal.getName();

        // system bypass
        // :FIXME: tmp hack
        if (username.equals("system")) {
            return true;
        }

        // Security Policy
        PolicyService policyService = Framework.getLocalService(PolicyService.class);
        if (policyService != null) {
            CorePolicyService corePolicyService = (CorePolicyService) policyService.getCorePolicy();
            if (corePolicyService != null
                    && principal instanceof NuxeoPrincipal) {
                if (!corePolicyService.checkPolicy(doc,
                        (NuxeoPrincipal) principal, permission)) {
                    return false;
                }
            }
        }

        // get the security store
        SecurityManager securityManager = doc.getSession().getRepository().getSecurityManager();

        // fully check each ACE in turn
        String[] resolvedPermissions = getPermissionsToCheck(permission);
        String[] additionalPrincipals = getPrincipalsToCheck(principal);

        // get the ordered list of ACE
        ACP acp = securityManager.getMergedACP(doc);

        // check pluggable policies
        Access access = securityPolicyService.checkPermission(doc, acp,
                principal, permission, resolvedPermissions,
                additionalPrincipals);
        if (access != null && !Access.UNKNOWN.equals(access)) {
            return access.toBoolean();
        }

        if (acp == null) {
            return false; // no ACP on that doc - by default deny
        }
        access = acp.getAccess(additionalPrincipals, resolvedPermissions);

        return access.toBoolean();
    }

    /**
     * Provides the full list of all permissions or groups of permissions that
     * contain the given one (inclusive).
     *
     * @param permission
     * @return the list, as an array of strings.
     */
    // TODO nicely expose for other (remote) services (Search...)
    public String[] getPermissionsToCheck(String permission) {
        String[] groups = permissionProvider.getPermissionGroups(permission);
        if (groups == null) {
            return new String[] { permission };
        } else {
            String[] perms = new String[groups.length + 1];
            perms[0] = permission;
            System.arraycopy(groups, 0, perms, 1, groups.length);
            return perms;
        }
    }

    protected String[] getPrincipalsToCheck(Principal principal) {
        List<String> userGroups = null;
        if (principal instanceof NuxeoPrincipal) {
            userGroups = ((NuxeoPrincipal) principal).getAllGroups();
        }
        if (userGroups == null) {
            return new String[] { principal.getName() };
        } else {
            String[] tmp = userGroups.toArray(new String[userGroups.size()]);
            String[] groups = new String[tmp.length + 1];
            groups[0] = principal.getName();
            System.arraycopy(tmp, 0, groups, 1, tmp.length);
            return groups;
        }
    }

    @Deprecated
    public boolean checkPermissionOld(Document doc, Principal principal,
            String permission) throws SecurityException {
        String username = principal.getName();

        // system bypass
        // :FIXME: tmp hack
        if (username.equals("system")) {
            return true;
        }

        // if document is locked by someone else the WRITE permission should be
        // disallowed
        try {
            String lock = doc.getLock();
            if (lock != null && !lock.startsWith(username + ':')
                    && permission.equals(SecurityConstants.WRITE)) {
                // locked by another user
                return false; // DENY WRITE
            }
        } catch (Exception e) {
            log.debug("Failed to get lock status on document ", e);
            // ignore
        }

        // get the security store
        SecurityManager securityManager = doc.getSession().getRepository().getSecurityManager();

        Access access = checkPermissionForUser(securityManager, doc, username,
                permission);
        if (access == Access.UNKNOWN) {

            // do the same for each user group if any
            if (principal instanceof NuxeoPrincipal) {
                List<String> userGroups = ((NuxeoPrincipal) principal).getAllGroups();
                if (userGroups != null && !userGroups.isEmpty()) {
                    for (String userGroup : userGroups) {
                        access = checkPermissionForUser(securityManager, doc,
                                userGroup, permission);
                        if (access != Access.UNKNOWN) {
                            break;
                        }
                    }
                }
            }
        }

        return access.toBoolean();
    }

    @Deprecated
    private Access checkPermissionForUser(SecurityManager securityManager,
            Document doc, String username, String permission)
            throws SecurityException {

        // first check the Everything pseudo permission
        // permission RESTRICTED_READ needs special handling
        // because it's a negative permission
        Access access;
        /*
         * if (!permission.equals(RESTRICTED_READ)) { access =
         * securityManager.getAccess(doc, username, EVERYTHING); if (access !=
         * Access.UNKNOWN) { return access; } }
         */
        // second check the given permission
        access = securityManager.getAccess(doc, username, permission);
        if (access != Access.UNKNOWN) {
            return access;
        }

        // third check the permission groups
        String[] permGroups = permissionProvider.getPermissionGroups(permission);
        if (permGroups != null) {
            for (String permGroup : permGroups) {
                access = securityManager.getAccess(doc, username, permGroup);
                if (access != Access.UNKNOWN) {
                    return access;
                }
            }
        }
        return Access.UNKNOWN;
    }

    public List<SecuritySummaryEntry> getSecuritySummary(Document doc,
            Boolean includeParents) {
        List<SecuritySummaryEntry> result = new ArrayList<SecuritySummaryEntry>();

        if (doc == null) {
            return result;
        }

        addChildrenToSecuritySummary(doc, result);
        // TODO: change API to use boolean instead
        if (includeParents.booleanValue()) {
            addParentsToSecurirySummary(doc, result);
        }
        return result;
    }

    private SecuritySummaryEntry createSecuritySummaryEntry(Document doc)
            throws DocumentException {
        return new SecuritySummaryEntryImpl(new IdRef(doc.getUUID()),
                new PathRef(doc.getPath()),
                doc.getSession().getSecurityManager().getACP(doc));
    }

    private void addParentsToSecurirySummary(Document doc,
            List<SecuritySummaryEntry> summary) {

        Document parent;
        try {
            parent = doc.getParent();
        } catch (DocumentException e) {
            return;
        }

        if (parent == null) {
            return;
        }

        try {
            SecuritySummaryEntry entry = createSecuritySummaryEntry(parent);
            final ACP acp = entry.getAcp();
            if (acp != null) {
                final ACL[] acls = acp.getACLs();
                if (acls != null && acls.length > 0) {
                    summary.add(0, entry);
                }
            }
        } catch (DocumentException e) {
            return;
        }

        addParentsToSecurirySummary(parent, summary);
    }

    private void addChildrenToSecuritySummary(Document doc,
            List<SecuritySummaryEntry> summary) {
        try {
            SecuritySummaryEntry entry = createSecuritySummaryEntry(doc);
            ACP acp = entry.getAcp();
            if (acp != null && acp.getACLs() != null
                    && acp.getACLs().length > 0) {
                summary.add(entry);
            }
        } catch (DocumentException e) {
            return;
        }
        try {
            Iterator<Document> iter = doc.getChildren();
            while (iter.hasNext()) {
                Document child = iter.next();
                addChildrenToSecuritySummary(child, summary);
            }
        } catch (DocumentException e) {
            // TODO Auto-generated catch block
            return;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter.isAssignableFrom(PermissionProvider.class)) {
            return (T) permissionProvider;
        } else if (adapter.isAssignableFrom(SecurityPolicyService.class)) {
            return (T) securityPolicyService;
        } else {
            return adapter.cast(this);
        }
    }

}