package com.bbva.datioamproduct.fdevdatio.steps

import java.nio.file.Files._
import java.nio.file.Paths
import com.datio.dataproc.sdk.launcher.SparkLauncher
import io.cucumber.scala.{EN, ScalaDsl}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.scalatest.Matchers
import scala.util.Try

class CrashCourseSteps extends ScalaDsl with EN with Matchers {

  private var givenProcessId = ""
  private var givenConfigPath = ""
  private var executionExitCode = -1
  private val name = "unittest"
  private val spark: SparkSession = SparkSession.builder().master("local").appName(name).getOrCreate()
  private var tables: Map[String, String] = Map("DEF" -> "no_data")
  private var testData: Map[String, DataFrame] = Map("DEF" -> spark.emptyDataFrame)

  Given("""The id of the process as {string}""") {
    processId: String => {
      givenProcessId = processId
    }
  }

  Given("""A config file with the contents:""") {
    content: String => {
      val configFile = Paths.get("target", "config/application.conf").toAbsolutePath
      Try(delete(configFile))
      Try(createDirectories(configFile.getParent))
      createFile(configFile)
      write(configFile, content.getBytes("UTF-8"))
      givenConfigPath = configFile.toString
    }
  }

  Given("""The env {string} targeting the resource file {string}""") {
    (env: String, resourceFile: String) => {
      setEnv(env, getTestResourcePath(resourceFile))
    }
  }

  Given("""The env {string} targeting the target file {string}""") {
    (env: String, resourceFile: String) => {
      setEnv(env, getTargetPath(resourceFile))
    }
  }

  When("""Executing the Launcher""") {
    () => {
      val args = Array(givenConfigPath, givenProcessId)
      executionExitCode = new SparkLauncher().execute(args)
    }
  }

  Then("""The exit code should be {int}""") {
    exitCode: Int =>
      executionExitCode shouldBe exitCode
  }

  private def setEnv(key: String, value: String) = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map = field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }

  private def getTestResourcePath(file: String): String = {
    val resource = Paths.get("src", "test", "resources", file)
    resource.toFile.getAbsolutePath
  }

  private def getTargetPath(file: String): String = {
    val resource = Paths.get("target", file)
    resource.toFile.getAbsolutePath
  }

  Given("""^a dataframe located at path (.*) with alias (\S+)$""") {
    (path: String, alias: String) => {
      tables += (alias -> path)
    }
  }

  When("""^I read (\S+) as dataframe$""") {
    (dfName: String) => {
      testData += (dfName -> spark.read.parquet(tables(dfName)))
    }
  }

  When("""^I filter (\S+) dataframe with filter (.*) and save it as (\S+)$""") {
    (dfName: String, filterCondition: String, alias: String) =>
      val tempData = testData(dfName).filter(filterCondition)
      testData += (alias -> tempData)
  }

  Then("""^records for (.*) dataframe are equal to (\d+)+$""") {
    (alias: String, records: Int) =>
      val data = testData(alias)
      data.count() shouldBe records
  }

  Then("""^(\S+) dataframe (is|is not) empty$""") {
    (dfName: String, comparison: String) =>
      withClue(s"$dfName dataframe does not match: $comparison empty. ") {
        val data = testData(dfName)
        comparison match {
          case "is" => data.rdd.isEmpty shouldBe true
          case "is not" => data.rdd.isEmpty shouldBe false
        }
      }
  }

  Then("""^the number of columns for (\S+) dataframe is (equal to|more than|less than) the number of columns for (\S+) dataframe$""") {
    (dfName1: String, comparison: String, dfName2: String) =>
      val data = testData(dfName1)
      val data2 = testData(dfName2)

      val df1Columns = data.columns.length
      val df2Columns = data2.columns.length

      withClue(s"Number of columns for $dfName1 dataframe is not $comparison than number of columns for $dfName2 dataframe: ") {
        comparison match {
          case "equal to" => df1Columns shouldBe df2Columns
          case "less than" => df1Columns should be < df2Columns
          case "more than" => df1Columns should be > df2Columns
        }
      }
  }

  Then("""^the number of columns for (\S+) dataframe is (equal to|more than|less than) (\d+)$""") {
    (dfName: String, comparison: String, numberOfColumns: Int) =>

      val df1Columns = testData(dfName).columns.length

      withClue(s"Number of columns for $dfName dataframe is not $comparison than $numberOfColumns: ") {
        comparison match {
          case "equal to" => df1Columns shouldBe numberOfColumns
          case "less than" => df1Columns should be < numberOfColumns
          case "more than" => df1Columns should be > numberOfColumns
        }
      }
  }

}
