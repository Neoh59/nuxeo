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

package org.nuxeo.ecm.core.repository.jcr;

import javax.jcr.RepositoryException;
import javax.jcr.query.QueryManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.core.query.Query;
import org.nuxeo.ecm.core.query.QueryException;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.QueryResult;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.FromClause;
import org.nuxeo.ecm.core.query.sql.model.FromList;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class JCRQuery implements Query {

    private static final Log log = LogFactory.getLog(JCRQuery.class);

    final JCRSession session;

    SQLQuery sqlQuery;

    private final String rawQuery;

    public JCRQuery(JCRSession session, String query) {
        rawQuery = query;
        this.session = session;
    }

    public QueryResult execute() throws QueryException {
        //log.debug("SQL query: " + rawQuery);
        try {
            sqlQuery = SQLQueryParser.parse(rawQuery);
            //log.info("!nxql Query: " + sqlQuery.getStatement());
            javax.jcr.query.Query jcrQuery = buildJcrQuery(sqlQuery);
            //log.info("!jcr Query: " + jcrQuery.getStatement());
            // run query within a controlable thread
            // use
            javax.jcr.query.QueryResult qr = jcrQuery.execute();
            return new JCRQueryResult(this, qr);
        } catch (RepositoryException e) {
            throw new QueryException("Failed to execute query", e);
        } catch (QueryParseException e) {
            throw new QueryException(e);
        }
    }

    public static String buildJCRQueryString(SQLQuery sqlQuery) {

        StringBuffer jcrq = new StringBuffer("SELECT ");

        // build the corresponding JCR query
        SelectClause sc = sqlQuery.getSelectClause();

        if (sc.isEmpty()) {
            jcrq.append('*');
        } else {
            jcrq.append(sc.get(0));
            for (int i = 1, size = sc.count(); i < size; i++) {
                jcrq.append(", ").append(sc.get(i));
            }
        }

        FromClause fc = sqlQuery.getFromClause();
        String type = fc.get(0);

        if ("document".equals(type) || fc.getType() == FromClause.LOCATION) {
            type = NodeConstants.ECM_NT_DOCUMENT.rawname;
        } else {
            type = TypeAdapter.docType2Jcr(type);
        }

        jcrq.append(" FROM ").append(type);

        WhereClause wc = sqlQuery.getWhereClause();
        if (wc != null) {
            jcrq.append(" WHERE ");
            SRD(wc.predicate, jcrq);
        }

        // add path restriction
        if (fc.getType() == FromClause.LOCATION) {
            if (wc == null) { // add WHERE clause if there is the case
                jcrq.append(" WHERE ");
            } else {
                jcrq.append(" AND ");
            }
            jcrq.append(buildPathRestriction(fc.elements));
        }

        OrderByClause obc = sqlQuery.getOrderByClause();
        if (obc != null) {
            jcrq.append(" ORDER BY ");
            for (Object element : obc.elements) {
                Reference reference = (Reference) element;
                jcrq.append(' ').append(reference.name).append(' ');
            }
            if (obc.isDescendent) {
                jcrq.append(" DESC ");
            }
        }

        return jcrq.toString();
    }

    private static String buildJcrPath(String s) {
        // trim quotes if present
        s = s.trim();
        if (s.startsWith("'") && s.endsWith("'")) {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append(" '/");
        buffer.append(NodeConstants.ECM_ROOT.rawname).append('/');
        buffer.append(ModelAdapter.path2Jcr(new Path(s.replaceAll("\\.", "/"))));
        buffer.append("%' ");
        return buffer.toString();
    }

    private static String buildPathRestriction(FromList fromList) {
        StringBuilder sb = new StringBuilder();
        sb.append("jcr:path LIKE ");
        // add workspace path
        sb.append(buildJcrPath(fromList.get(0)));
        int size = fromList.size();
        for (int i = 1; i < size; i++) {
            sb.append("OR jcr:path LIKE ");
            sb.append(buildJcrPath(fromList.get(i)));
        }
        if (size > 1) {
            sb.insert(0, " ( ").append(" ) ");
        }
        return sb.toString();
    }

    private static void SRD(Operand operand, StringBuffer buff) {
        if (null == operand) {
            return;
        }
        if (operand instanceof Expression) {
            Expression expression = (Expression) operand;

            if (expression.lvalue.toString().equals("ecm:path")) {
                if (expression.operator.equals(Operator.STARTSWITH)) {
                    // log.info(operand);

                    buff.append("jcr:path LIKE ");
                    buff.append(buildJcrPath(expression.rvalue.toString()));
                    buff.append("");
                    return;
                } else {
                    log.warn("Operator '" + expression.operator
                            + "' not supported for ecm:path. Ignored.");
                    return;
                }
            }
            SRD(expression.lvalue, buff);
            buff.append(' ');
            buff.append(expression.operator.toString());
            buff.append(' ');
            SRD(expression.rvalue, buff);
            return;
        }
        buff.append(operand.toString());
    }

    public javax.jcr.query.Query buildJcrQuery(SQLQuery sqlQuery)
    throws QueryException {
        return buildXPathJcrQuery(sqlQuery);
        //return buildSqlJcrQuery(sqlQuery);
    }

    public javax.jcr.query.Query buildSqlJcrQuery(SQLQuery sqlQuery)
    throws QueryException {
        try {
            final String jcrQuery = buildJCRQueryString(sqlQuery);
            final QueryManager qm = session.jcrSession().getWorkspace().getQueryManager();
            return qm.createQuery(jcrQuery, javax.jcr.query.Query.SQL);
        } catch (RepositoryException e) {
            throw new QueryException("Invalid JCR query", e);
        }
    }

    public javax.jcr.query.Query buildXPathJcrQuery(SQLQuery sqlQuery)
    throws QueryException {
        try {
            final String jcrQuery = XPathBuilder.fromNXQL(sqlQuery);
            //System.out.println(">>>>> "+jcrQuery);
            final QueryManager qm = session.jcrSession().getWorkspace().getQueryManager();
            return qm.createQuery(jcrQuery, javax.jcr.query.Query.XPATH);
        } catch (RepositoryException e) {
            throw new QueryException("Invalid JCR query", e);
        }
    }

    @Override
    public String toString() {
        return rawQuery;
    }

}