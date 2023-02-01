package belfius.bpms.processes.test.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.jbpm.bpmn2.handler.SendSignalAction;
import org.jbpm.bpmn2.xml.XmlBPMNProcessDumper;
import org.jbpm.workflow.core.impl.JavaDroolsAction;
import org.jbpm.workflow.core.node.EndNode;
import org.jbpm.workflow.core.node.EventNode;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.WorkflowProcess;

import belfius.bpms.processes.test.model.ProcessNode;
import belfius.bpms.processes.test.model.ProcessNode.NodeType;

public class TemplateWriter {
	 
    public static void main(String[] args) {
    	
    	System.out.println();
    	System.out.println("Usage: execute a maven task with this command: "
    			+ "'mvn exec:java -Dexec.mainClass=belfius.bpms.processes.test.template.TemplateWriter -Dexec.args=[\"arg1\" \"arg2\" \"arg3\"]");
    	System.out.println();
    	System.out.println("Example assuming your bpmn model is under src/main/resources/com/sample:");
    	System.out.println("      mvn exec:java -Dexec.mainClass=\"belfius.bpms.processes.test.template.TemplateWriter\" "
    			+ "-Dexec.args=[\"com/sample/CreditProcess.bpmn\" \"com.sample.somepackage\" \"CreditProcess\"]");
    	System.out.println();
    	
    	if(args == null || args.length < 2) {
    		throw new IllegalArgumentException("Wrong arguments. Please provide 1) The file path (starting from src/main/resources), 2) the class package name, 3) the process name");
    	}
    	
    	String processPath = args[0];
    	String packageName = args[1];
    	String processId = args[2];
    	
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init();
        
        Template t = velocityEngine.getTemplate("velocity-templates/test-helper-template.vm");
        
        VelocityContext context = new VelocityContext();
                
        InputStream bpmnProcess = TemplateWriter.class.getClassLoader().getResourceAsStream(processPath);

        String bpmnProcessAsString;
		try {
			bpmnProcessAsString = new String(bpmnProcess.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
        
		WorkflowProcess process = (WorkflowProcess)XmlBPMNProcessDumper.INSTANCE.readProcess(bpmnProcessAsString);
        
        Node[] nodes = process.getNodes();
        List<ProcessNode> userTasks = new ArrayList<ProcessNode>();
        List<ProcessNode> serviceTasks = new ArrayList<ProcessNode>();
        List<ProcessNode> intermediateCatchingEvents = new ArrayList<ProcessNode>();
        List<ProcessNode> endEvents = new ArrayList<ProcessNode>();
        List<ProcessNode> endThrowingSignalEvents = new ArrayList<ProcessNode>();
        
        for (int i = 0; i < nodes.length; i++) {
			Node node = nodes[i];
			
			if(node.getNodeType().equals(org.kie.api.definition.process.NodeType.WORKITEM_TASK)) {
				
				ProcessNode processNode = new ProcessNode(((WorkItemNode)node).getName(), ((WorkItemNode)node).getWork().getName(), NodeType.SERVICE_TASK);
				
				if(!serviceTasks.contains(processNode))
					serviceTasks.add(processNode);
			}
			
			if(node.getNodeType().equals(org.kie.api.definition.process.NodeType.HUMAN_TASK)) {
				
				ProcessNode processNode = new ProcessNode(((WorkItemNode)node).getName(), ((WorkItemNode)node).getWork().getName(), NodeType.USER_TASK);
				
				if(!userTasks.contains(processNode))
					userTasks.add(processNode);
			}
			
			if(node.getNodeType().equals(org.kie.api.definition.process.NodeType.CATCH_EVENT)) {
				
				ProcessNode processNode = new ProcessNode(((EventNode)node).getName(), ((EventNode)node).getType(), NodeType.INTERMEDIATE_CATCHING_EVENT);
				
				if(!intermediateCatchingEvents.contains(processNode))
					intermediateCatchingEvents.add(processNode);
			}
			
			if(node.getNodeType().equals(org.kie.api.definition.process.NodeType.END)) {
				
				ProcessNode processNode = new ProcessNode(((EndNode)node).getName(), null, NodeType.END_EVENT);
				
				if(((EndNode)node).getName() != null && !((EndNode)node).getName().equals("")) {
					if(((EndNode)node).getActions("onEntry") != null
							&& ((EndNode)node).getActions("onEntry").size() > 0
							&& ((JavaDroolsAction)((EndNode)node).getActions("onEntry").get(0)).getAction() instanceof SendSignalAction) {						
						if(!endThrowingSignalEvents.contains(processNode))
							endThrowingSignalEvents.add(processNode);
					}
					else
					{
						if(!endEvents.contains(processNode))
							endEvents.add(processNode);
					}
				}
			}
			
		}
  
        if(packageName != null) {
            context.put("packagename", packageName);
        }
  
        context.put("processId", processId);
        context.put("userTasks", userTasks);
        context.put("serviceTasks", serviceTasks);
        context.put("intermediateCatchingEvents", intermediateCatchingEvents);
        context.put("endThrowingSignalEvents", endThrowingSignalEvents);
        context.put("endEvents", endEvents);
  
        StringWriter writer = new StringWriter();
        t.merge( context, writer );
  
        System.out.println(writer.toString());
    }
}