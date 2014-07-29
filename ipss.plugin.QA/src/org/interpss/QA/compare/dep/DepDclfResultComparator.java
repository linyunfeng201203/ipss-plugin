package org.interpss.QA.compare.dep;

import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.QA.result.QAResultContainer;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.DclfAlgorithm;
import com.interpss.core.dclf.common.ReferenceBusException;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Utility class for compare Dclf results. There are two main steps:
 *  
 *  1) Load some network parameter and solved Dclf results into the qaResultSet object
 *  2) Compare network parameter stored in the this.net object
 *  3) Compare dclf result by running dclf or stored in the dclfAlgo object
 * 
 * @author mzhou
 *
 */
@Deprecated
public class DepDclfResultComparator	extends DepNetModelComparator<QAAclfBusRec, QAAclfBranchRec> {
	private DclfAlgorithm algo = null;
	
	public DepDclfResultComparator() {
		this.qaResultSet = new  QAResultContainer<QAAclfBusRec, QAAclfBranchRec>(100.0);
	}
	
	public DepDclfResultComparator(AclfNetwork net, QAResultContainer<QAAclfBusRec, QAAclfBranchRec> qaResultSet) {
		super(net, qaResultSet);
	}
	
	/**
	 * set Dclf Algo with solved Dclf results
	 * 
	 * @param algo
	 */
	public void setDclfAlgo( DclfAlgorithm algo) {
		this.algo = algo;
		this.net = algo.getNetwork();
	}
	
	/**
	 * set Dclf result to the resultSet object
	 * 
	 * @param algo
	 */
	public DepDclfResultComparator setBaseResult(DclfAlgorithm algo) {
		AclfNetwork net = algo.getNetwork();
		
		for (AclfBus bus : net.getBusList())
			if (bus.isActive()) {
				QAAclfBusRec rec = new QAAclfBusRec(bus.getId());
				this.qaResultSet.setBusResult(bus.getId(), rec);
				rec.vmag = 1.0*bus.getBaseVoltage();
				rec.vang = algo.getBusAngle(bus.getId());
				rec.genp = bus.isRefBus()? algo.getBusPower(bus) : bus.getGenP();
				rec.loadp = bus.getLoadP();
				rec.shuntg = bus.getShuntY().getReal();
			}
		
		for (AclfBranch branch : net.getBranchList()) {
			String fromId = branch.getFromBus().getId();
			String toId = branch.getToBus().getId();
			String cirId = branch.getCircuitNumber();
			QAAclfBranchRec rec = new QAAclfBranchRec(fromId, toId, cirId);
			this.qaResultSet.setBranchResult(branch.getId(), rec);

			rec.id = branch.getId();
			rec.onStatus = branch.isActive();        // by default the onStatus = true
			
			rec.branchType = branch.isLine() ?   // Line, Breaker, Transformer
						QAAclfBranchRec.BranchType.Line : 
							( (branch.isPSXfr()||branch.isPSXfr()) ? QAAclfBranchRec.BranchType.Xformer : QAAclfBranchRec.BranchType.Breaker);   
			double flow = 0.0;
			if (branch.isActive())
				flow = algo.getBranchFlow(branch);
		
			rec.from_p = flow;
		}
		
		return this;
	}	
	
	/**
	 * Compare PsXfr shifting angle
	 * 
	 * @return
	 */
	public boolean comparePsXfrShiftAngle() {
		this.errMsgList.clear();
		int cnt = 0;
		for (Branch bra : net.getBranchList()) {
			if (bra.isActive()) {
				AclfBranch branch = (AclfBranch)bra;
				if (branch.isPSXfr()) {
					cnt++;
					QAAclfBranchRec rec = this.qaResultSet.getBranchResult(bra.getId());
					double ang = branch.toPSXfr().getFromAngle();
					if (!NumericUtil.equals(rec.fromShiftAng, ang, VAngErr)) {
						String msg = "PsXfr shift ang mismatch: Branch -" + bra.getId() + ", " + 
								 String.format("%5.4f(r), %5.4f(m), %4.3f", rec.fromShiftAng, ang,  
								     Math.toDegrees(Math.abs(rec.fromShiftAng-ang))) + " deg";
						//IpssLogger.getLogger().warning(msg);
						addErrMsg(msg);
					}
				}
			}
		}
		System.out.println("Total PsXfr: " + cnt);
		return true;
	}
	
	/**
	 * Compare network parameters and data before Dclf, including gen/load 
	 * at each bus and total gen/load in the network.
	 * 
	 * @return
	 */
	public boolean compareNetParamData() {
		this.errMsgList.clear();

		// before running, bus genP and loadP should be the same 
		
		double totalGenModel = 0.0, totalLoadModel = 0.0,
				totalGenResult = 0.0, totalLoadResult = 0.0;
		for (Bus bus : this.net.getBusList()) {
			if (bus.isActive()) {
				AclfBus aclfBus = (AclfBus)bus; 
				QAAclfBusRec rec = this.qaResultSet.getBusResult(bus.getId());

				// compare base voltage against the NomVolt in the result file
				double v = aclfBus.getBaseVoltage();
				double rv = QAAclfBusRec.vUnit == UnitType.kV? rec.vmag*1000.0 : rec.vmag;
				if ((rec.genp != 0.0 || v != 0.0) && !NumericUtil.equals(rv, v, VMagErr)) {
					double base = rv!=0?rv:v;
					String msg = "VMag mismatch: Bus-" + rec.id + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rv, v,  
							     Math.abs(100.0*(rv - v)/base)) + "%";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}

				// compare genp
				if (!this.net.isRefBus(aclfBus)) {
					double genp = aclfBus.getGenP();
					if ((rec.genp != 0.0 || genp != 0.0) && !NumericUtil.equals(rec.genp, genp, PQErr)) {
						double base = rec.genp!=0?rec.genp:genp;
						String msg = "GenP mismatch: Bus-" + rec.id + ", " + 
								 String.format("%5.4f(r), %5.4f(m), %4.3f", rec.genp, genp,  
								     Math.abs(100.0*(rec.genp - genp)/base)) + "%";
						//IpssLogger.getLogger().warning(msg);
						addErrMsg(msg);
					}
					totalGenModel += genp;
					totalGenResult += rec.genp;
				}

				// compare loadp
				double loadp = aclfBus.getLoadP();
				if ((rec.loadp != 0.0 || loadp != 0.0) && !NumericUtil.equals(rec.loadp, loadp, PQErr)) {
					double base = rec.loadp!=0?rec.loadp:loadp;
					String msg = "LoadP mismatch: Bus-" + rec.id + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rec.loadp, loadp,  
							     Math.abs(100.0*(rec.loadp - loadp)/base)) + "%";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}
				totalLoadModel += loadp;
				totalLoadResult += rec.loadp;
			}
		}
		
		//System.out.println("Total model gen, load " + totalGenModel + "  " + totalLoadModel);
		//System.out.println("Total result gen, load " + totalGenResult + "  " + totalLoadResult);
		
		// compare total gen
		if (!NumericUtil.equals(totalGenResult, totalGenModel, PQErr)) {
			String msg = "Total system Gen mismatch: " + 
					 String.format("%5.4f(r), %5.4f(m), %4.3f", totalGenResult, totalGenModel,  
					     Math.abs(100.0*(totalGenResult - totalGenModel)/totalGenResult)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}

		// compare total load
		if (!NumericUtil.equals(totalLoadResult, totalLoadModel, PQErr)) {
			String msg = "Total system Load mismatch: " + 
					 String.format("%5.4f(r), %5.4f(m), %4.3f", totalLoadResult, totalLoadModel,  
					     Math.abs(100.0*(totalLoadResult - totalLoadModel)/totalLoadResult)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}

		return true;
	}

	/**
	 * It first run Dclf on the AclfNetwork object and then compare the result
	 * store in the resultSet object, including bus angle, branch angle and branch
	 * flow
	 * 
	 * @param refBusAng reference bus angle offset
	 * @param angErrDeg angle error for comparison
	 * @param pErr power error for comparison
	 * @param busResult bus result for output to a file
	 * @param branchResult branch result for output to a file
	 * @return
	 */
	public boolean compareDclfResult(double refBusAng,	double angErrDeg, double pErr) {
		return compareDclfResult(refBusAng, angErrDeg, pErr, false);	}
	public boolean compareDclfResult(double refBusAng,	double angErrDeg, double pErr, boolean applyAdjust) {
		StringBuffer busResult = new StringBuffer();
		busResult.append("BusId,BusAng(deg),Gen(mw),Load(mv)\n");
		StringBuffer branchResult = new StringBuffer();
		branchResult.append("FromBusId,ToBusId,CirId,PFlow(mv)\n");
		return compareDclfResult(refBusAng, angErrDeg, pErr, busResult, branchResult, applyAdjust);	}
	public boolean compareDclfResult(double refBusAng, 
			double angErrDeg, double pErr, 
			StringBuffer busResult, StringBuffer branchResult,
			boolean applyAdjust) {
		this.errMsgList.clear();

		if (this.algo == null) {
			this.algo = DclfObjectFactory.createDclfAlgorithm(net, applyAdjust);
			//DclfAlgorithm algo = DclfObjectFactory.createDclfAlgorithm(net, false);
			try {
				algo.calculateDclf();
			} catch (InterpssException | ReferenceBusException
					| IpssNumericException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
		
		// compare bus angle
		
		double baseMva = net.getBaseKva() * 0.001;
		double max = 0.0;
		for (Bus b : algo.getNetwork().getBusList()) {
			if (b.isActive()) {
				AclfBus bus = (AclfBus)b;
				int n = bus.getSortNumber();
				double angDeg = Math.toDegrees(algo.getBusAngle(n)) + refBusAng;
				QAAclfBusRec rec = this.qaResultSet.getBusResult(bus.getId());
				double rangDeg = Math.toDegrees(rec.vang);
				if (Math.abs(rangDeg-angDeg) > max )
					max = Math.abs(rangDeg-angDeg);
				if ((rangDeg != 0.0 || angDeg != 0.0) && !NumericUtil.equals(rangDeg, angDeg, angErrDeg)) {
					String msg = "VAng mismatch: Bus-" + rec.id + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rangDeg, angDeg,  
							     Math.abs(rangDeg-angDeg)) + " deg";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}
				busResult.append(String.format("%s, %5.4f, %5.4f, %4.2f\n", bus.getId(), 
						angDeg, bus.getGenP()*baseMva, bus.getLoadP()*baseMva));
			}
		}
		addErrMsg("Max bus angle diff: " + max + " deg");

		// compare branch angle difference
		
		max = 0.0;
		for (Branch bra : algo.getNetwork().getBranchList()) {
			if (bra.isActive()) {
				int fn = bra.getFromBus().getSortNumber();
				int tn = bra.getToBus().getSortNumber();
					
				// compare angle
				double angDeg = Math.toDegrees(algo.getBusAngle(fn) - algo.getBusAngle(tn));
				QAAclfBusRec frec = this.qaResultSet.getBusResult(bra.getFromPhysicalBusId());
				QAAclfBusRec trec = this.qaResultSet.getBusResult(bra.getToPhysicalBusId());
				double rangDeg = Math.toDegrees(frec.vang - trec.vang);
				if (Math.abs(rangDeg-angDeg) > max )
					max = Math.abs(rangDeg-angDeg);
				if ((rangDeg != 0.0 || angDeg != 0.0) && !NumericUtil.equals(rangDeg, angDeg, angErrDeg)) {
					String msg = "VAng mismatch: Branch -" + bra.getId() + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rangDeg, angDeg,  
							     Math.abs(rangDeg-angDeg)) + " deg";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}
			}
		}
		addErrMsg("Max branch angle diff: " + max + " deg");

		// algo.getBranchFlow(algo.getAclfNetwork().getAclfBranch("Bus3771->Bus3807(1)"));

		// compare branch flow 
		
		max = 0.0;
		for (Branch bra : algo.getNetwork().getBranchList()) {
			if (bra.isActive()) {
				AclfBranch aclfBra = (AclfBranch)bra;				
				double pflow = algo.getBranchFlow(aclfBra);
				
				
				// compare pflow
				QAAclfBranchRec rec = this.qaResultSet.getBranchResult(bra.getId());

				if (Math.abs(rec.from_p-pflow) > max )
					max = Math.abs(rec.from_p-pflow);
				
				if ((pflow != 0.0 || rec.from_p != 0.0) && !NumericUtil.equals(rec.from_p, pflow, pErr)) {
					String msg = "Pflwo mismatch: Branch -" + bra.getId() + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rec.from_p, pflow,  
							     Math.abs(rec.from_p-pflow)) + " pu";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}
				branchResult.append(String.format("%s, %s, %s, %5.4f\n", bra.getFromPhysicalBusId(), 
						bra.getToPhysicalBusId(), bra.getCircuitNumber(), pflow * baseMva));
			}
		}
		addErrMsg("Max branch power diff: " + max + " pu");
		
		algo.destroy();
		
		return this.errMsgList.size() == 3;  // there are three status msg
	}

	public String outDclfResult(double angOffset) throws InterpssException, ReferenceBusException, IpssNumericException {
		DclfAlgorithm algo = DclfObjectFactory.createDclfAlgorithm(net);
		algo.calculateDclf();
		
		StringBuffer buf = new StringBuffer();
		buf.append("                -------    Dclf  --------     -------   Result --------\n");
		buf.append("   Bud Id       VoltAng(deg)     Gen/Load     VoltAng(deg)     Gen/Load\n");
		buf.append("========================================================================\n");
		
		for (Bus bus : algo.getNetwork().getBusList()) {
			if (bus.isActive()) {
				AclfBus aclfBus = (AclfBus)bus; 
				int n = bus.getSortNumber();
				double angDeg = Math.toDegrees(algo.getBusAngle(n)) - angOffset;
				double pPu = (aclfBus.getGenP() - aclfBus.getLoadP()); 
				
				QAAclfBusRec busRec = this.qaResultSet.getBusResult(bus.getId());
				
				buf.append(Number2String.toFixLengthStr(8, bus.getId()) + "        "
						+ String.format("%8.2f", angDeg) + "         "
						+ ((pPu != 0.0)? String.format("%8.2f",pPu) : "        ") 
						+ String.format("     %8.2f", Math.toDegrees(busRec.vang)) + "         "
						+ ((pPu != 0.0)? String.format("%8.2f", (busRec.genp - busRec.loadp)) : "") + "\n"); 
			}
		}

		algo.destroy();
		
		return buf.toString();
	}
}
