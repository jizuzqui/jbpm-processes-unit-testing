package belfius.bpms.processes.test;

import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.Map;

import org.jbpm.kie.services.impl.KModuleDeploymentUnit;
import org.jbpm.process.audit.JPAAuditLogService;
import org.jbpm.services.api.model.DeploymentUnit;
import org.jbpm.test.services.AbstractKieServicesTest;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.task.TaskService;
import org.kie.internal.runtime.conf.RuntimeStrategy;

/**
 * 
 * @author izuzqj
 * @see https://github.com/kiegroup/jbpm/blob/7.59.x/jbpm-services/jbpm-kie-services/src/test/java/org/jbpm/kie/services/test/ProcessServiceImplPerProcessInstanceTest.java
 * @see https://github.com/kiegroup/jbpm/blob/7.59.x/jbpm-test/src/main/java/org/jbpm/test/services/AbstractKieServicesTest.java
 * @see https://github.com/kiegroup/jbpm/blob/7.59.x/jbpm-test/src/main/java/org/jbpm/test/services/AbstractServicesTest.java
 */
public class BelfiusKieBaseTest extends AbstractKieServicesTest {
	private TaskService taskService = null;
	private RuntimeEngine runtimeEngine = null;
	private KieSession ksession = null;
	private Map<String, ResourceType> definitionsMap = null;
	private Map<Long, WorkItem> activeWorkItemMap = null;
	private JPAAuditLogService auditLogService = null;

	
    protected static final String ARTIFACT_ID = "4EBACItsme-NaturalPersons";
    protected static final String GROUP_ID = "com.geob";
    protected static final String VERSION = "1.0.6-SNAPSHOT";
    
    protected String puName = "com.geob:4EBACItsme-NaturalPersons:1.0.0-SNAPSHOT";

	@Override
	protected List<String> getProcessDefinitionFiles() {
		// TODO Auto-generated method stub
		return null;
	}
//	
//	@Before
//	@Override
//	public void setUp () throws Exception {
//		setPuName(puName);
//		 prepareDocumentStorage();
//	    configureServices();
//	    deploymentUnit = prepareDeploymentUnit();
//	}
	
	@Override
    protected DeploymentUnit createDeploymentUnit(String groupId, String artifactid, String version) throws Exception {
        DeploymentUnit unit = super.createDeploymentUnit(groupId, artifactid, version);
        ((KModuleDeploymentUnit) unit).setStrategy(RuntimeStrategy.PER_PROCESS_INSTANCE);
        return unit;
    }

	@Override
	protected DeploymentUnit prepareDeploymentUnit() throws Exception {
		return createAndDeployUnit("com.geob", "4EBACItsme-NaturalPersons", "1.0.6-SNAPSHOT"); 
	}
	
	@Test
	public void test_1 () {
		long processInstanceId = processService.startProcess(deploymentUnit.getIdentifier(), "_4EBACItsme-NaturalPersons.BACItsMe_mainProcess");
		
		assertNotNull(processInstanceId);
	}
}
