package eu.assist_iot.ipsm.core.functions

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.jena.atlas.lib.Lib
import org.apache.jena.query.QueryBuildException
import org.apache.jena.sparql.expr.{ExprEvalException, ExprList, NodeValue}
import org.apache.jena.sparql.function.FunctionBase

class unixEpochToUTC extends FunctionBase {

  override def checkBuild(uri: String, args: ExprList): Unit = {
    if (args.size != 1) throw new QueryBuildException("Function '" + Lib.className(this) + "' takes one argument")
  }

  def exec(args: java.util.List[NodeValue]): NodeValue = {
    if (args.size != 1) {
      throw new ExprEvalException("unixEpochToUTC: Wrong number of arguments: " + args.size + " : [expected 1]")
    }
    val epoch = nodeToLong(args.get(0))
    val res = new SimpleDateFormat("yyy:MM:dd HH:mm:ss").format(new Date(epoch * 1000L))
    NodeValue.makeString(res)
  }

  def nodeToLong(nv: NodeValue): Long = {
    import org.apache.jena.datatypes.xsd.XSDDatatype
    val node = nv.asNode
    val lit = node.getLiteral
    if (!XSDDatatype.XSDinteger.isValidLiteral(lit)) {
      throw new IllegalArgumentException("unixEpochToUTC: argument is not a long value")
    } else {
      lit.getValue.asInstanceOf[Number].longValue
    }
  }
}

