/*
 * (C) Copyright 2006-2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nicolas Ulrich
 *
 */

package org.nuxeo.ecm.platform.jbpm;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.DocumentModel;

public class TaskListImpl implements TaskList {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(TaskListImpl.class);

    protected DocumentModel doc;

    public TaskListImpl(DocumentModel doc) {
        this.doc = doc;
    }

    @SuppressWarnings("unchecked")
    public void addTask(VirtualTaskInstance task) {
        try {

            ArrayList<Map<String, Object>> newList = new ArrayList<Map<String, Object>>();

            List<Map<String, Object>> currentList = (List<Map<String, Object>>) doc.getPropertyValue("ntl:tasks");

            if (currentList != null) {
                newList.addAll(currentList);
            }

            Map<String, Object> persone = new HashMap<String, Object>();
            persone.put("users", task.getActors());
            persone.put("directive", task.getDirective());
            persone.put("comment", task.getComment());
            persone.put("dueDate", task.getDueDate());

            newList.add(persone);

            doc.setPropertyValue("ntl:tasks", newList);

        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<VirtualTaskInstance> getTasks() {
        try {
            List<VirtualTaskInstance> mls = new ArrayList<VirtualTaskInstance>();
            List<Map<String, Object>> participants = (List<Map<String, Object>>) doc.getPropertyValue("ntl:tasks");

            if (participants != null) {
                for (Map<String, Object> participant : participants) {
                    VirtualTaskInstance task = new VirtualTaskInstance();
                    task.setActors((List<String>)participant.get("users"));
                    task.setDirective((String)participant.get("directive"));
                    task.setComment((String)participant.get("comment"));
                    task.setDueDate(((GregorianCalendar)participant.get("dueDate")).getTime());
                    mls.add(task);
                }
            }

            return mls;

        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    public DocumentModel getDocument() {
        return this.doc;
    }

    public String getName() {
        try {
            return this.doc.getTitle();
        } catch (ClientException e) {
            log.error(e);
        }

        return null;

    }

    public String getUUID() {
        return this.doc.getId();
    }

}