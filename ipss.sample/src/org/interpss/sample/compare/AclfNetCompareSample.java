package org.interpss.sample.compare;

import org.interpss.IpssCorePlugin;
import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.util.sample.SampleCases;

public class AclfNetCompareSample {

	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
  		AclfNetwork net1 = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net1);
		AclfNetBean netBean1 = new AclfNet2BeanMapper().map2Model(net1);
		
		AclfNetwork net2 = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net2);
		AclfNetBean netBean2 = new AclfNet2BeanMapper().map2Model(net2);
		
		/*
		 * No difference case
		 */
		// compare output msg written to the netBean1.msgList
		netBean1.setCompareLog(BaseJSONBean.CompareLog.MsgList);
		netBean1.compareTo(netBean2);
		System.out.println("Difference: " + netBean1.getMsgList().size());
		
		/*
		 * Has difference case
		 */
		// compare output msg written to the netBean1.msgList
		net2.getBus("1").setVoltageMag(0.95);
		netBean2 = new AclfNet2BeanMapper().map2Model(net2);
		netBean1.compareTo(netBean2);
		System.out.println("Difference: " + netBean1.getMsgList().size());
		System.out.println(netBean1.getMsgList());
	}
}
