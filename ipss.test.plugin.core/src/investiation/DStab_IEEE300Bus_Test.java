package investiation;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_IEEE300Bus_Test  extends DStabTestSetupBase{
		
		@Test
		public void test_IEEE300_Dstab() throws InterpssException{
			IpssCorePlugin.init();
			IpssLogger.getLogger().setLevel(Level.OFF);
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/adpter/psse/v30/IEEE300/IEEE300Bus_modified_noHVDC.raw",
					"testData/adpter/psse/v30/IEEE300/IEEE300_dyn_v2.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    BaseDStabNetwork<?, ?> dsNet =simuCtx.getDStabilityNet();
		    
		    //dsNet.setBypassDataCheck(true);
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			
			aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

			aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
			aclfAlgo.setTolerance(1.0E-6);
			assertTrue(aclfAlgo.loadflow());
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.002);
			dstabAlgo.setTotalSimuTimeSec(10.0);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus10030-mach1"));
			

			StateMonitor sm = new StateMonitor();
			
			sm.addBusStdMonitor(new String[]{"Bus10000","Bus10001","Bus10015","Bus10016","Bus10028"});
			sm.addGeneratorStdMonitor(new String[]{"Bus10003-mach1","Bus10005-mach1","Bus10008-mach1","Bus10009-mach1"});
	
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(25);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
			
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10003",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0,0),null,1.0d,0.08),"3phaseFault@Bus17");
			

			if (dstabAlgo.initialization()) {
				double t1 = System.currentTimeMillis();
				dstabAlgo.performSimulation();
				double t2 = System.currentTimeMillis();
			}
			
			assertTrue(sm.getMachSpeedTable().get("Bus10003-mach1").get(0).getValue()-sm.getMachSpeedTable().get("Bus10003-mach1").get(10).getValue()<1.0E-4);
			assertTrue(sm.getMachSpeedTable().get("Bus10009-mach1").get(0).getValue()-sm.getMachSpeedTable().get("Bus10009-mach1").get(10).getValue()<1.0E-4);
			
		}
}
