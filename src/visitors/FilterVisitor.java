package visitors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import main.RGraph;
import main.RGraphBuilder;
import main.SingleQuery;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpWalker;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprFunction0;
import org.apache.jena.sparql.expr.ExprFunction1;
import org.apache.jena.sparql.expr.ExprFunction2;
import org.apache.jena.sparql.expr.ExprFunction3;
import org.apache.jena.sparql.expr.ExprFunctionN;
import org.apache.jena.sparql.expr.ExprFunctionOp;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.ExprVisitor;
import org.apache.jena.sparql.expr.ExprWalker;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.aggregate.Aggregator;
import org.apache.jena.sparql.graph.GraphFactory;

//import cl.uchile.dcc.blabel.label.GraphColouring.HashCollisionException;

/**
 * A class based on Jena's ExprVisitor that creates an r-graph that represents a FILTER expression.
 * @author Jaime
 *
 */
public class FilterVisitor implements ExprVisitor {
	
	private Stack<Node> nodeStack = new Stack<Node>();
	private RGraph filterGraph;

	public FilterVisitor(){
		filterGraph = new RGraph(NodeFactory.createBlankNode(), GraphFactory.createPlainGraph(), Collections.<Var> emptyList());
	}
	
	@Override
	public void visit(ExprFunction0 func) {
		nodeStack.add(filterGraph.filterFunction(NodeFactory.createLiteral(func.getFunctionSymbol().getSymbol()), Collections.<Node>emptyList()));
	}

	@Override
	public void visit(ExprFunction1 func) {
		Node arg = nodeStack.pop();
		List<Node> args = List.of(arg);
		if (func.getOpName() != null) {
			if (func.getOpName().equals("!")){
				nodeStack.add(filterGraph.filterNot(arg));
			}
			else{
				nodeStack.add(filterGraph.filterFunction(func.getOpName(), arg));
			}
		}
		else {
			nodeStack.add(filterGraph.filterFunction(func.getFunctionSymbol().getSymbol(), arg));
		}		
	}

	@Override
	public void visit(ExprFunction2 func) {
		Node arg2 = nodeStack.pop();
		Node arg1 = nodeStack.pop();
		if (func.getOpName() != null) {
			if (func.getOpName().equals("&&")){
				nodeStack.add(filterGraph.filterAnd(arg1, arg2));
			}
			else if (func.getOpName().equals("||")){
				nodeStack.add(filterGraph.filterOr(arg1, arg2));
			}
			else{
				nodeStack.add(filterGraph.filterFunction(func.getOpName(), arg1, arg2));
			}
		}
		else {
			nodeStack.add(filterGraph.filterFunction(func.getFunctionSymbol().getSymbol(), arg1, arg2));
		}
	}

	@Override
	public void visit(ExprFunction3 func) {
		Node arg3 = nodeStack.pop();
		Node arg2 = nodeStack.pop();
		Node arg1 = nodeStack.pop();
		List<Node> args = new ArrayList<Node>();
		args.add(arg1);
		args.add(arg2);
		args.add(arg3);
		nodeStack.add(filterGraph.filterFunction(NodeFactory.createLiteral(func.getFunctionSymbol().getSymbol()), args));
	}

	@Override
	public void visit(ExprFunctionN func) {
		List<Node> nodes = new ArrayList<Node>();
		for (int i = 0; i < func.getArgs().size(); i++) {
			nodes.add(nodeStack.pop());
		}
		Collections.reverse(nodes);
		if (func.getFunctionIRI() != null) {
			nodeStack.add(filterGraph.filterFunction(NodeFactory.createURI(func.getFunctionIRI()), nodes));
		}
		else {
			nodeStack.add(filterGraph.filterFunction(NodeFactory.createLiteral(func.getFunctionSymbol().getSymbol()), nodes));
		}
	}

	@Override
	public void visit(ExprFunctionOp funcOp) {
		Op op = SingleQuery.UCQTransformation(funcOp.getGraphPattern());
		RGraphBuilder rb = new RGraphBuilder();
		OpWalker.walk(op, rb);
//		try {
			RGraph r = rb.getResult();
			//RGraph r1 = r.getCanonicalForm(false);
			GraphUtil.addInto(filterGraph.graph, r.graph);
			nodeStack.add(filterGraph.filterFunction(funcOp.getFunctionSymbol().getSymbol(),r.root));
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		} catch (HashCollisionException e) {
//			e.printStackTrace();
//		}
	}

	@Override
	public void visit(NodeValue nv) {
		Node n = nv.asNode();
		if (n.isVariable()) {
			n = NodeFactory.createBlankNode(n.getName());
		}
		if (n.isLiteral()) {
			if (n.getLiteralLanguage().equals("")) {
				n = NodeFactory.createLiteralByValue(n.getLiteralValue().toString(), n.getLiteralDatatype());
			}
			else {
				n = NodeFactory.createLiteral(n.getLiteralValue().toString()+"@"+n.getLiteralLanguage());
			}
		}
		nodeStack.add(n);
	}

	@Override
	public void visit(ExprVar nv) {
		nodeStack.add(NodeFactory.createBlankNode(nv.getVarName()));	
	}

	@Override
	public void visit(ExprAggregator eAgg) {
		ExprList eList = eAgg.getAggregator().getExprList();
		if (eList == null) {
			Aggregator agg = eAgg.getAggregator();
			ExprVar var = eAgg.getAggVar();
			Node n = NodeFactory.createBlankNode();
			nodeStack.add(filterGraph.aggregationCount(agg.getName(), var.asVar(), n));
		}
		else {
			List<Expr> exprs = eList.getList();
			for (Expr e : exprs) {
				if (e instanceof ExprVar) {
					nodeStack.add(filterGraph.aggregationFunction(eAgg.getAggregator().getName(), eAgg.getVar(), NodeFactory.createBlankNode(e.getVarName())));
				}
				else {
					FilterVisitor fv = new FilterVisitor();
					ExprWalker.walk(fv, e);
					RGraph r = fv.getGraph();
					nodeStack.add(r.root);
					nodeStack.add(filterGraph.aggregationFunction(eAgg.getAggregator().getName(), eAgg.getVar(), nodeStack.peek()));
					GraphUtil.addInto(filterGraph.graph, r.graph);
				}
			}	
		}	
	}
	
	public RGraph getGraph(){
		filterGraph.root = nodeStack.peek();
		filterGraph.filterNormalisation();
		return this.filterGraph;
	}

}