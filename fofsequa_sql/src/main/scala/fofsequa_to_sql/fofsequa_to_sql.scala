package fofsequa_to_sql
import org.nanquanu.fofsequa._
import org.nanquanu.fofsequa_reasoner._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object Fofsequa_to_sql {
  // Constructs a list of SQL queries for creating the required tables
  lazy val create_tables = tables.map( {
    case Table(table_name, fields) =>
      s"CREATE TABLE $table_name" ++
        fields.map({case Field(field_name, data_type) =>
          field_name ++ " " ++ data_type
        }).mkString("(", ",", ");")
  })

  val tables = List(
    Table("field_of_interest", List(
      Field("id", "int"),
      Field("name", "VARCHAR(255)"),
      Field("parent_id", "int"),
    )),

    Table("nq_project", List(
      Field("id", "int"),
      Field("name", "VARCHAR(255)"),
    )),

    Table("project_interesting_to", List(
      Field("id", "int"),
      Field("nq_project_id", "int"),
      Field("field_of_interest_id", "int"),
    )),
  )

  def sql_from_kb(file_name: String): Option[List[String]] = {
    val kb = FolseqParser.parseAll(FolseqParser.fofsequa_document, io.Source.fromFile(file_name).mkString) match {
      case FolseqParser.NoSuccess(msg, input) => { println(msg); return None }
      case FolseqParser.Success(parsed, _next) => parsed
    }

    var fois = ListBuffer[Field_of_interest]()
    var project_interesting_to = ListBuffer[Project_interesting_to]()
    var project_names = collection.mutable.Set[String]()
    var next_foi_id = 0
    var next_interesting_id = 0

    // Get all sub_field_of and interesting_to statements
    for(statement <- kb) {
      statement match {
        case AtomStatement(predicate, terms) => {
          if(terms.length == 2 && terms.forall(_.isInstanceOf[ConstantTerm])) {
            // This is a valid sub_field_of or field_of_interest statement
            val names = terms.map(_.asInstanceOf[ConstantTerm].constant.id.name)

            if(predicate.name.name == "sub_field_of") {
              // format is: sub_field_of('child_name', 'parent_name')
              fois.append(Field_of_interest(
                names(0),
                names(1),
                next_foi_id
              ))

              next_foi_id += 1
            }
            else if(predicate.name.name == "interesting_to") {
              // format is: interesting_to('project_name', 'field_of_interest_name')

              project_names.add(names(0))

              project_interesting_to.append(Project_interesting_to(
                names(0),
                names(1),
                next_interesting_id
              ))

              next_interesting_id += 1
            }
          }
          else {
            // TODO: error, incorrect format
          }
        }
        case _ => () // TODO: ERROR or ignore?
      }
    }

    val projects_with_id = project_names.zip(0 to project_names.size)
    val project_name_to_id = mutable.HashMap.empty[String, Int]
    val foi_name_to_id = mutable.HashMap.empty[String, Int]

    for((name, id) <- projects_with_id) {
      project_name_to_id.update(name, id)
    }

    for(Field_of_interest(name, _, id) <- fois) {
      foi_name_to_id.update(name, id)
    }

    None
  }
}

case class Field_of_interest(name: String, parent_name: String, id: Int)
case class Project_interesting_to(project_name: String, foi_name: String, id: Int)

case class Field(name: String, data_type: String)
case class Table(name: String, fields: Seq[Field])