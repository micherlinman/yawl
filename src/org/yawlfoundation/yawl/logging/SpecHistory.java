/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.logging.table.*;
import org.yawlfoundation.yawl.util.HibernateEngine;
import org.yawlfoundation.yawl.util.StringUtil;
import org.yawlfoundation.yawl.util.XNode;

import java.util.*;

/**
 * @author Michael Adams
 * @date 4/12/16
 */
public class SpecHistory {

    private final Map<String, Set<Object[]>> _dataMap = new HashMap<String, Set<Object[]>>();
    private final Logger _log = LogManager.getLogger(this.getClass());
    private final List<String> eventDescriptorsWithData = new ArrayList<String>();

    private static final String EVENT_QUERY = "select ni,ti,e,t" +
            " FROM YLogNetInstance ni, YLogTaskInstance ti, YLogEvent e, YLogTask t" +
            " WHERE e.instanceID = ti.taskInstanceID" +
            " AND t.taskID = ti.taskID"+
            " AND e.rootNetInstanceID = ni.netInstanceID" +
            " AND NOT e.descriptor in ('NetStart', 'NetComplete')" +
            " AND ni.netID = (:id)";

    private static final String DATA_QUERY = "select di,dt" +
            " FROM YLogNetInstance ni, YLogTaskInstance ti, YLogEvent e," +
            " YLogDataItemInstance di, YLogDataType dt" +
            " WHERE e.instanceID = ti.taskInstanceID" +
            " AND (e.descriptor='DataValueChange' or di.descriptor like 'Predicate%')" +
            " AND di.eventID=e.eventID" +
            " AND dt.dataTypeID=di.dataTypeID" +
            " AND e.rootNetInstanceID = ni.netInstanceID" +
            " AND ni.netID = (:id)";


    public SpecHistory() { 
        setEventDescriptorsWithData();
     }

    private void setEventDescriptorsWithData() {
        eventDescriptorsWithData.add("Executing");
        eventDescriptorsWithData.add("Complete");
        eventDescriptorsWithData.add("CaseComplete");
        eventDescriptorsWithData.add("CaseStart");
        eventDescriptorsWithData.add("DataValueChange");
    }

    public XNode get(HibernateEngine logDb, long specKey, boolean withData) {
        processDataResults(getDataEvents(logDb, specKey));
        return processResults(getEvents(logDb, specKey), withData);
    }

    
    private List getEvents(HibernateEngine logDb, long specKey) {
        return get(logDb, specKey, EVENT_QUERY);
    }


    private List getDataEvents(HibernateEngine logDb, long specKey) {
        return get(logDb, specKey, DATA_QUERY);
    }


    private List get(HibernateEngine logDb, long specKey, String query) {
        return logDb.createQuery(query).setLong("id", specKey).list();
    }


    private XNode processResults(List events, boolean withData) {
        _log.debug("XES #process: begins");
        Map<String, XNode> caseMap = new TreeMap<String, XNode>();
        for (Object o : events) {
            Object[] array = (Object[]) o;
            YLogNetInstance netInstance = (YLogNetInstance) array[0];
            YLogTaskInstance taskInstance = (YLogTaskInstance) array[1];
            YLogEvent eventInstance = (YLogEvent) array[2];
            YLogTask task = (YLogTask) array[3];

            XNode caseNode = getOrCreateCaseNode(caseMap, netInstance.getEngineInstanceID());
            XNode netNode = getOrCreateNetNode(caseNode, netInstance.getNetInstanceID());
            XNode taskNode = getOrCreateTaskNode(netNode, taskInstance.getTaskInstanceID(),
                    task, taskInstance.getEngineInstanceID());

            String eventDescriptor = eventInstance.getDescriptor();
            // don't include data change events if withData is false
            if (withData || (!eventDescriptor.equals("DataValueChange"))) {
                XNode eventNode = getOrCreateEventNode(taskNode, eventInstance.getEventID(),
                        eventDescriptor, eventInstance.getTimestampString());

                if(eventDescriptorsWithData.contains(eventDescriptor)) {
                    boolean hasData = _dataMap.get(eventNode.getAttributeValue("id")) != null;

                    if(hasData) {
                        addDataNodes(eventNode);
                    }
                }
            }
        }

        XNode cases = new XNode("cases");
        List<XNode> caseNodes = new ArrayList<XNode>(caseMap.values());
        Collections.sort(caseNodes, new Comparator<XNode>() {
            @Override
            public int compare(XNode n1, XNode n2) {
                String c1 = n1.getAttributeValue("id");
                String c2 = n2.getAttributeValue("id");
                return StringUtil.strToInt(c1, 0) - StringUtil.strToInt(c2, 0);
            }
        });
        cases.addChildren(caseNodes);
        _log.debug("XES #process: ends");
        return cases;
    }


    private XNode getOrCreateCaseNode(Map<String, XNode> caseMap, String caseID) {
        XNode caseNode = caseMap.get(caseID);
        if (caseNode == null) {
            caseNode = new XNode("case");
            caseNode.addAttribute("id", caseID);
            caseMap.put(caseID, caseNode);
        }
        return caseNode;
    }


    private XNode getOrCreateNetNode(XNode caseNode, long instanceID) {
        return getOrCreateNode(caseNode, instanceID,"netinstance");
    }


    private XNode getOrCreateTaskNode(XNode netNode, long instanceID, YLogTask task,
                                      String engineInstanceID) {
        XNode taskNode = getChildNode(netNode, instanceID);
        if (taskNode == null) {
            taskNode = netNode.addChild("taskinstance");
            taskNode.addAttribute("id", instanceID);
            if (task != null) {
                taskNode.addChild("taskname", task.getName());
            }
            taskNode.addChild("engineinstanceid", engineInstanceID);
        }
        return taskNode;
    }


    private XNode getOrCreateEventNode(XNode taskNode, long instanceID,
                                       String eventDescriptor, String timestamp) {
        XNode eventNode = getChildNode(taskNode, instanceID);
        if (eventNode == null) {
            eventNode = taskNode.addChild("event");
            eventNode.addAttribute("id", instanceID);
            eventNode.addChild("descriptor", eventDescriptor);
            eventNode.addChild("timestamp", timestamp);
        }
        return eventNode;
    }


    private void addDataNodes(XNode eventNode) {
        XNode dataItemNode = eventNode.getOrAddChild("dataItems");

        for (Object[] array : _dataMap.get(eventNode.getAttributeValue("id"))) {
            XNode dataNode = dataItemNode.addChild("dataItem");
            YLogDataItemInstance dataItemInstance = (YLogDataItemInstance) array[0];
            YLogDataType dataType = (YLogDataType) array[1];
            dataNode.addChild("descriptor", dataItemInstance.getDescriptor());
            dataNode.addChild("name", dataItemInstance.getName());
            dataNode.addChild("value", dataItemInstance.getValue());
            if (dataType != null) {
                dataNode.addChild("typeName", dataType.getName());
                dataNode.addChild("typeDefinition", dataType.getDefinition());
            }
        }
    }


    private XNode getOrCreateNode(XNode parent, long instanceID, String childName) {
        XNode node = getChildNode(parent, instanceID);
        if (node == null) {
            node = parent.addChild(childName);
            node.addAttribute("id", instanceID);
        }
        return node;
    }


    private XNode getChildNode(XNode parent, long instanceID) {
        XNode node = null;
        for (XNode child : parent.getChildren()) {
            String id = child.getAttributeValue("id");
            if (id != null && id.equals(String.valueOf(instanceID))) {
                node = child;
                break;
            }
        }
        return node;
    }


    private void processDataResults(List dataValues) {
        for (Object o : dataValues) {
            Object[] array = (Object[]) o;
            YLogDataItemInstance dataItemInstance = (YLogDataItemInstance) array[0];
            String id = String.valueOf(dataItemInstance.getEventID());
            Set<Object[]> set = _dataMap.get(id);
            if (set == null) {
                set = new HashSet<Object[]>();
                _dataMap.put(id, set);
            }
            set.add(array);
        }
    }

}
