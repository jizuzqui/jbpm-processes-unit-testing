package belfius.bpms.processes.test.model;

public class ProcessNode {
	
	public enum NodeType {
		// Start and end events
		START_EVENT(org.kie.api.definition.process.NodeType.START.name()),
		END_EVENT(org.kie.api.definition.process.NodeType.END.name()),
		// Atomic activities
		USER_TASK(org.kie.api.definition.process.NodeType.HUMAN_TASK.name()),
		SERVICE_TASK(org.kie.api.definition.process.NodeType.WORKITEM_TASK.name()),
		SCRIPT_TASK(org.kie.api.definition.process.NodeType.SCRIPT_TASK.name()),
		BUSINESS_RULE_TASK(org.kie.api.definition.process.NodeType.BUSINESS_RULE.name()),
		// Events
		INTERMEDIATE_CATCHING_EVENT(org.kie.api.definition.process.NodeType.CATCH_EVENT.name()),
		INTERMEDIATE_THROWING_EVENT(org.kie.api.definition.process.NodeType.THROW_EVENT.name()),
		BOUNDARY_EVENT(org.kie.api.definition.process.NodeType.BOUNDARY_EVENT.name()),
		ERROR_EVENT(org.kie.api.definition.process.NodeType.FAULT.name()),
		CATCHING_LINK_EVENT(org.kie.api.definition.process.NodeType.CATCH_LINK.name()),
		THROWING_LINK_EVENT(org.kie.api.definition.process.NodeType.THROW_LINK.name()),
		TIMER_EVENT(org.kie.api.definition.process.NodeType.TIMER.name()),
		// Gateways
		EVENT_BASED_GATEWAY(org.kie.api.definition.process.NodeType.EVENT_BASED_GATEWAY.name()),
		EXCLUSIVE_GATEWAY(org.kie.api.definition.process.NodeType.EXCLUSIVE_GATEWAY.name()),
		INCLUSIVE_GATEWAY(org.kie.api.definition.process.NodeType.INCLUSIVE_GATEWAY.name()),
		PARALLEL_GATEWAY(org.kie.api.definition.process.NodeType.PARALLEL_GATEWAY.name()),
		// Composite nodes
		SUBPROCESS(org.kie.api.definition.process.NodeType.SUBPROCESS.name()),
		EVENT_SUBPROCESS(org.kie.api.definition.process.NodeType.EVENT_SUBPROCESS.name());
		
		private String nodeType;
		
		private NodeType(String nodeType) {
			setNodeType(nodeType);
		}

		public String getNodeType() {
			return nodeType;
		}

		private void setNodeType(String nodeType) {
			this.nodeType = nodeType;
		}
	};
	
	private String nodeName;
	private String nodeId;
	private NodeType nodeType;
	
	public ProcessNode(String nodeName, String nodeId, NodeType nodeType) {
		this.setNodeName(nodeName);
		this.setNodeId(nodeId);
		this.setNodeType(nodeType);
	}
	
	public String getNodeName() {
		return nodeName;
	}
	
	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}
	
	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
	
	public NodeType getNodeType() {
		return nodeType;
	}
	public void setNodeType(NodeType nodeType) {
		this.nodeType = nodeType;
	}
}
