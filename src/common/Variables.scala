package scala.virtualization.lms
package common

import java.io.PrintWriter
import scala.virtualization.lms.util.OverloadHack

trait Variables extends Base with OverloadHack {
  type Var[+T]

  implicit def readVar[T:Manifest](v: Var[T]) : Rep[T]
  //implicit def chainReadVar[T,U](x: Var[T])(implicit f: Rep[T] => U): U = f(readVar(x))

  def __newVar[T](init: Rep[T])(implicit o: Overloaded1, mT: Manifest[T]): Var[T]
  def __assign[T:Manifest](lhs: Var[T], rhs: Rep[T]) : Rep[Unit]

  def infix_+=[T:Numeric:Manifest](lhs: Var[T], rhs: T) = var_plusequals(lhs,rhs)

  def var_plusequals[T:Numeric:Manifest](lhs: Var[T], rhs: T) : Rep[Unit]
}

trait VariablesExp extends Variables with EffectExp {
  type Var[+T] = Variable[T]
  // TODO: make a design decision here.
  // defining Var[T] as Sym[T] is dangerous. If someone forgets to define a more-specific implicit conversion from
  // Var[T] to Ops, e.g. implicit def varToRepStrOps(s: Var[String]) = new RepStrOpsCls(varToRep(s))
  // then the existing implicit from Rep to Ops will be used, and the ReadVar operation will be lost.
  // Defining Vars as separate from Exps will always cause a compile-time error if the implicit is missing.
  //type Var[T] = Sym[T]

  // read operation
  implicit def readVar[T:Manifest](v: Var[T]) : Exp[T] = reflectEffect(ReadVar(v))

  case class ReadVar[T:Manifest](v: Var[T]) extends Def[T]
  case class NewVar[T:Manifest](init: Exp[T]) extends Def[T]
  case class Assign[T:Manifest](lhs: Var[T], rhs: Exp[T]) extends Def[Unit]
  case class VarPlusEquals[T:Manifest:Numeric](lhs: Var[T], rhs: T) extends Def[Unit]


  def __newVar[T](init: Exp[T])(implicit o: Overloaded1, mT: Manifest[T]): Var[T] = {
    //reflectEffect(NewVar(init)).asInstanceOf[Var[T]]
    Variable(reflectEffect(NewVar(init)))
  }

  def __assign[T:Manifest](lhs: Var[T], rhs: Exp[T]): Exp[Unit] = {
    reflectMutation(Assign(lhs, rhs))
    Const()
  }

  def var_plusequals[T:Numeric:Manifest](lhs: Var[T], rhs: T) = reflectMutation(VarPlusEquals(lhs, rhs))
}


trait ScalaGenVariables extends ScalaGenEffect {
  val IR: VariablesExp
  import IR._

  override def emitNode(sym: Sym[_], rhs: Def[_])(implicit stream: PrintWriter) = rhs match {
    case ReadVar(Variable(a)) => emitValDef(sym, quote(a))
    case NewVar(init) => emitVarDef(sym, quote(init))
    case Assign(Variable(a), b) => emitAssignment(quote(a), quote(b))
    //case Assign(a, b) => emitAssignment(quote(a), quote(b))
    case VarPlusEquals(Variable(a), b) => emitValDef(sym, quote(a) + " += " + quote(b))
    case _ => super.emitNode(sym, rhs)
  }
}

trait CudaGenVariables extends CudaGenEffect {
  val IR: VariablesExp
  import IR._

  override def emitNode(sym: Sym[_], rhs: Def[_])(implicit stream: PrintWriter) = {
      rhs match {
        case ReadVar(Variable(a)) =>
          emitValDef(sym, quote(a))
        case NewVar(init) =>
          emitVarDef(sym, quote(init))
        case Assign(Variable(a), b) =>
          emitAssignment(quote(a), quote(b))
        case _ => super.emitNode(sym, rhs)
      }
    }
}