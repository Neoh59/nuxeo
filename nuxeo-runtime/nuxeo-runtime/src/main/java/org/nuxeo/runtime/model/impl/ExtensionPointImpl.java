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

package org.nuxeo.runtime.model.impl;

import java.io.Serializable;

import org.nuxeo.common.xmap.XMap;
import org.nuxeo.common.xmap.annotation.XContent;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.common.xmap.annotation.XParent;
import org.nuxeo.runtime.model.Extension;
import org.nuxeo.runtime.model.ExtensionPoint;
import org.nuxeo.runtime.model.RegistrationInfo;
import org.w3c.dom.Element;

/**
 * @author  <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
@XObject
public class ExtensionPointImpl implements ExtensionPoint, Serializable {

    private static final long serialVersionUID = 3959978759388449332L;

    @XNode("@name")
    public String name;

    @XContent("documentation")
    public String documentation;

    @XNodeList(value = "object@class", type = Class[].class, componentType = Class.class)
    public transient Class[] contributions;

    public transient XMap xmap;

    @XParent
    public transient RegistrationInfo ri;


    public Class[] getContributions() {
        return contributions;
    }

    public String getName() {
        return name;
    }

    public String getDocumentation() {
        return documentation;
    }

    public Extension createExtension(Element element) {
        return null;
    }

    public Object[] loadContributions(final RegistrationInfo owner, Extension extension) throws Exception {
        if (contributions != null) {
            if (xmap == null) {
                xmap = new XMap();
                for (Class contrib : contributions) {
                    xmap.register(contrib);
                }
            }
            Object[] contribs = xmap.loadAll(
                    new XMapContext(extension.getContext()),
                    extension.getElement());
            extension.setContributions(contribs);
            return contribs;
        }
        return null;
    }

}