/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     vpasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.ecm.restapi.server.jaxrs.blob;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.webengine.model.Resource;
import org.nuxeo.ecm.webengine.model.WebAdapter;
import org.nuxeo.ecm.webengine.model.impl.DefaultAdapter;

/**
 * REST API Blob Manager
 *
 * @since 5.7.3
 */
@WebAdapter(name = BlobAdapter.NAME, type = "blobAdapter")
public class BlobAdapter extends DefaultAdapter {

    public static final String NAME = "blob";

    protected DocumentModel doc;

    @Path("{fieldPath:((?:(?!/@).)*)}")
    public Resource doGet(@PathParam("fieldPath") String fieldPath) {
        doc = getTarget().getAdapter(DocumentModel.class);
        return newObject("blob", fieldPath, doc);
    }

}
