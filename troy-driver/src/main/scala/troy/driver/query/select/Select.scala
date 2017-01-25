package troy
package driver.query.select

import com.datastax.driver.core._
import shapeless._
import shapeless.ops.hlist.ZipWithIndex
import shapeless.ops.nat.ToInt
import troy.driver.CassandraDataType
import troy.driver.schema.{ KeyspaceExists, TableExists, VersionExists }
import troy.driver.JavaConverters.RichListenableFuture
import troy.driver.codecs.TroyCodec
import troy.driver.JavaConverters.RichListenableFuture

import scala.annotation.implicitNotFound
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.macros.blackbox.Context

/*
 * PInfo: Compile time info about schema
 * Params: type of params to be sent in runtime
 */
trait StatementBinder[PInfo, Params] {
  def bind(bs: BoundStatement, params: Params): BoundStatement
}


object StatementBinder {
  def instance[PInfo, Params <: HList](binder: (BoundStatement, Params) => BoundStatement) = new StatementBinder[PInfo, Params] {
    override def bind(bs: BoundStatement, params: Params): BoundStatement = binder(bs, params)
  }

  implicit val hNilInstance = instance[HNil, HNil]((s, _) => s)

  implicit def hListInstance[Index <: Nat, CassandraType <: CassandraDataType, ScalaType, PInfoTail <: HList, ParamsTail <: HList](
    implicit
    index: ToInt[Index],
    headCodec: TroyCodec[CassandraType, ScalaType],
    tailBinder: StatementBinder[PInfoTail, ParamsTail]
  ) = instance[(CassandraType, Index) :: PInfoTail, ScalaType :: ParamsTail] { (statement, params) =>
    val newS = headCodec.set(statement, Nat.toInt[Index], params.head)
    tailBinder.bind(newS, params.tail)
  }

  //
  //    implicit def implicitNotFoundMacro[X, Ps <: HList]: StatementBinder[X, Ps] = macro implicitNotFoundMacroImpl[Ps]
  //
  //    def implicitNotFoundMacroImpl[Ps](c: Context) = c.abort(c.enclosingPosition, s"a7a need 3ala fekra")
}

/*
 * QInfo: Compile time info about schema
 * Out: generic representation of required Scala Row representation
 */
trait RowParser[QInfo, Out <: HList] {
  def parse(row: Row): Out
}
object RowParser {
  def instance[QInfo, Out <: HList](p: Row => Out) = new RowParser[QInfo, Out] {
    override def parse(row: Row): Out = p(row)
  }

  implicit val hNilInstance = instance[HNil, HNil](_ => HNil)

  implicit def hListInstance[CassandraType <: CassandraDataType, Index <: Nat, QInfoTail <: HList, ScalaType, OutTail <: HList](
    implicit
    index: ToInt[Index],
    headCodec: TroyCodec[CassandraType, ScalaType],
    tailParser: RowParser[QInfoTail, OutTail]
  ) = instance[(CassandraType, Index) :: QInfoTail, ScalaType :: OutTail] { row =>
    headCodec.get(row, Nat.toInt[Index]) :: tailParser.parse(row)
  }
}

trait Select[I, O] {
  def apply(input: I): Future[Iterable[O]]
}

object Select {
  // TODO
  // trait Aliased[S, Alias]
  trait Column[Name]
  trait Function[Keyspace, Name, Params <: HList]
  // trait Asterisk
  trait NoKeyspace // Used to mark absence of a specific keyspace

  trait Relation[ColumnName, O, T]

  //  trait Operator
  /**
   * Denotes ==, <, >, >, <=, >=, !=
   */
  trait Equality
  trait In
  trait Contains
  trait ContainsKey
  trait Like

  trait Term
  trait AnonymousBindMarker extends Term
  trait NamedBindMarker[Name] extends Term
  }
}