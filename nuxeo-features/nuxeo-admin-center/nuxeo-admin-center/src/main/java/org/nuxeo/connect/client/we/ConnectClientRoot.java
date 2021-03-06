/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 */

package org.nuxeo.connect.client.we;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.nuxeo.ecm.admin.NuxeoCtlManager;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.webengine.model.Access;
import org.nuxeo.ecm.webengine.model.Resource;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;

/**
 * Root object: mainly acts as a router.
 *
 * @author <a href="mailto:td@nuxeo.com">Thierry Delprat</a>
 */
@Path("/connectClient")
@WebObject(type = "connectClientRoot", administrator = Access.GRANT)
public class ConnectClientRoot extends ModuleRoot {

    @Path(value = "packages")
    public Resource listPackages() {
        return ctx.newObject("packageListingProvider");
    }

    @Path(value = "download")
    public Resource download() {
        return ctx.newObject("downloadHandler");
    }

    @Path(value = "install")
    public Resource install() {
        return ctx.newObject("installHandler");
    }

    @Path(value = "uninstall")
    public Resource uninstall() {
        return ctx.newObject("uninstallHandler");
    }

    @Path(value = "remove")
    public Resource remove() {
        return ctx.newObject("removeHandler");
    }

    @GET
    @Produces("text/html")
    @Path(value = "restartView")
    public Object restartServerView() {
        if (((NuxeoPrincipal) getContext().getPrincipal()).isAdministrator()) {
            return getView("serverRestart").arg("nuxeoctl", new NuxeoCtlManager());
        } else {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

    @GET
    @Produces("text/html")
    @Path(value = "registerInstanceCallback")
    public Object registerInstanceCallback(@QueryParam("ConnectRegistrationToken") String token) {
        return getView("registerInstanceCallback").arg("token", token);
    }
}
