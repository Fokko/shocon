/* Copyright 2016 UniCredit S.p.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.typesafe.config

import java.util.{concurrent => juc}
import java.{time => jt, util => ju}

import eu.unicredit.shocon
import eu.unicredit.shocon.Config.Value
import eu.unicredit.shocon.Extractor

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.experimental.macros

object ConfigFactory {
  def parseString(s: String): Config = {
    println("have to parse dynamically string")
    new Config(() => shocon.Config("{}"))
  }

  import eu.unicredit.shocon.ConfigLoader

  def load(): Config = macro ConfigLoader.loadDefaultImpl

  def load(cl: ClassLoader): Config = macro ConfigLoader.loadDefaultImplCL

  def defaultReference(): Config = macro ConfigLoader.loadDefaultImpl

  def defaultReference(cl: ClassLoader): Config = macro ConfigLoader.loadDefaultImplCL

  def empty() = Config(() => shocon.Config("{}"))

  def parseMap(values: java.util.Map[String, Any]) =
    parseString(values.asScala.map{ case (k, v) => s"$k=$v"}.mkString("\n"))

  def load(conf: Config): Config = conf
}

case class Config(initial_cfg: () => shocon.Config.Value) { self =>
  lazy val cfg = {
    // println("parser called ... :-(")
    // need to put this in place back...
    // initial_cfg()
    shocon.Config("{}")
  }
  import shocon.ConfigOps
  import shocon.Extractors._

  lazy val fallbackStack: mutable.Queue[shocon.Config.Value] = {
    println("fallback")
    mutable.Queue(cfg)
  }

  def this() = {
    this(() => shocon.Config("{}"))
  }

  def root() = {
    println("called root")
    new ConfigObject() {
      override val inner = null

      override def toConfig = {
        val res = Config(() => shocon.Config("{}"))
        res.initialCache = cache
        res
      }
      def unwrapped =  ???
        //cache.asInstanceOf[Map[String,Any]].asJava
        // cache.map{
        //   case (k,v) =>
        //     //(k -> v.unwrapped)
        //     (k -> v)
        // }.asJava
      def entrySet() = ???
    }
    // new ConfigObject() {
    //   val inner = null
    //   def unwrapped = cache.map{
    //     case (k,v) => (k -> v.unwrapped)
    //   }.asJava
    //   def entrySet = null
      // lazy val inner = self.cfg
      // def unwrapped =
      //   cfg.as[shocon.Config.Object].get.unwrapped.asJava
      // def entrySet(): ju.Set[ju.Map.Entry[String, ConfigValue]] =
      //   cfg.as[shocon.Config.Object].get.fields.mapValues(v => new ConfigValue() {
      //     override val inner: Value = v
      //   }).asJava.entrySet()
    // }
  }

  var initialCache = mutable.Map[String, Value]()

  lazy val cache: mutable.Map[String, Value] = initialCache

  def entrySet(): ju.Set[ju.Map.Entry[String, ConfigValue]] = root.entrySet()

  def checkValid(c: Config, paths: String*): Unit = {}

  def resolve(): Config = this

  def withFallback(c: Config) = {
    println("adding fallback?????")

    println(s"1-> cache is $cache")
    println(s"2-> cache is ${c.initialCache}")
    // cache ++= c.cache
    // fallbackStack.enqueue(c.cfg)
    this
  }

  val prePath = ""

  def getOrReturnNull[T](path: String)(implicit ev: Extractor[T]): T = {
    // lazy val res =
    //   fallbackStack
    //     .find(_.get(path).isDefined)
    //     .flatMap(_.get(path)).orNull

    // val fullPath = s"$path"
    try {
      ev(
        cache.get(prePath + path) match {
          case Some(elem) =>
            println("elem is "+elem)
            try { elem } catch {
              case _: Throwable =>
                println("wrong type for "+path)
                // res
                eu.unicredit.shocon.Config.NullLiteral
                //null.asInstanceOf[T]
            }
          case _ =>
            println("cache miss for "+path)
            // cache.update(fullPath, res)
            // res
            eu.unicredit.shocon.Config.NullLiteral
            //null.asInstanceOf[T]
        })
      } catch {
        case err: Throwable =>
          // println(s"cache is $cache")
          null.asInstanceOf[T]
          // throw err
      }
  }

  def hasPath(path: String): Boolean =
    fallbackStack.exists(_.get(path).isDefined)

  def getConfig(path: String) = {
    val res = new Config(() => shocon.Config("{}")) {
      override def root() = self.root()
      override val prePath = path + "."
    }
    res.initialCache = initialCache
    // getOrReturnNull[shocon.Config.Value](path))
    // println(s"adding config: $path")
    // res.initialCache = initialCache.flatMap{
    //   case (k, v) if k.startsWith(path) =>
    //     println(s"adding config: $k as "+ k.replace(s"$path.", ""))
    //     Some(k.replace(s"$path.", "") -> v)
    //   case _ => None
    // }
    res
  }

  def getString(path: String) = getOrReturnNull[String](path)

  def getBoolean(path: String): Boolean = getOrReturnNull[Boolean](path)

  def getInt(path: String) = getOrReturnNull[Int](path)

  def getLong(path: String) = getOrReturnNull[Long](path)

  def getDouble(path: String) = getOrReturnNull[Double](path)

  def getBytes(path: String): Long = {
    val bytesValue = getString(path)
    parseBytes(bytesValue, path)
  }

  /**
    * Parses a size-in-bytes string. If no units are specified in the string,
    * it is assumed to be in bytes. The returned value is in bytes. The purpose
    * of this function is to implement the size-in-bytes-related methods in the
    * Config interface.
    *
    * @param input
    *            the string to parse
    * @param pathForException
    *            path to include in exceptions
    * @return size in bytes
    * @throws ConfigException
    *             if string is invalid
    */
  def parseBytes(input: String, pathForException: String): Long = {
    val s: String = unicodeTrim(input)
    val unitString: String = getUnits(s)
    val numberString: String = unicodeTrim(
      s.substring(0, s.length() - unitString.length()))

    // this would be caught later anyway, but the error message
    // is more helpful if we check it here.
    if (numberString.length() == 0) {
      throw ConfigException.BadValue(pathForException)
    }
    val units: Option[MemoryUnit] = MemoryUnit.parseUnit(unitString)

    if (units.isEmpty) {
      throw ConfigException.BadValue(pathForException)
    }

    try {
      val unitBytes = units.get.bytes
      val result: BigInt =
        // if the string is purely digits, parse as an integer to avoid
        // possible precision loss; otherwise as a double.
        if (numberString.matches("[0-9]+")) {
          unitBytes * BigInt(numberString)
        } else {
          val resultDecimal: BigDecimal = BigDecimal(unitBytes) * BigDecimal(
              numberString)
          resultDecimal.toBigInt()
        }

      if (result.bitLength < 64) {
        result.longValue()
      } else {
        throw ConfigException.BadValue(pathForException)
      }
    } catch {
      case e: NumberFormatException =>
        throw ConfigException.BadValue(pathForException)
    }
  }

  def getStringList(path: String): ju.List[String] =
    getOrReturnNull[ju.List[String]](path) match {
      case null => List[String]().asJava
      case ret => ret
    }

  def getDuration(path: String, unit: TimeUnit): Long = {
    val durationValue = getString(path)
    val nanos = parseDurationAsNanos(durationValue)
    unit.convert(nanos, juc.TimeUnit.NANOSECONDS)
  }

  def getDuration(path: String): jt.Duration = {
    val durationValue = getString(path)
    val nanos = parseDurationAsNanos(durationValue)
    return jt.Duration.ofNanos(nanos)
  }

  def parseDurationAsNanos(input: String): Long = {
    import juc.TimeUnit._

    val s: String = unicodeTrim(input)
    val originalUnitString: String = getUnits(s)
    var unitString: String = originalUnitString
    val numberString: String = unicodeTrim(
      s.substring(0, s.length - unitString.length))

    if (numberString.length == 0)
      throw new ConfigException.BadValue(
        "No number in duration value '" + input + "'")
    if (unitString.length > 2 && !unitString.endsWith("s"))
      unitString = unitString + "s"

    val units = unitString match {
      case "" | "ms" | "millis" | "milliseconds" => MILLISECONDS
      case "us" | "micros" | "microseconds" => MICROSECONDS
      case "d" | "days" => DAYS
      case "h" | "hours" => HOURS
      case "s" | "seconds" => SECONDS
      case "m" | "minutes" => MINUTES
      case _ =>
        throw new ConfigException.BadValue(
          "Could not parse time unit '" + originalUnitString + "' (try ns, us, ms, s, m, h, d)")
    }

    try {
      // return here
      if (numberString.matches("[0-9]+")) units.toNanos(numberString.toLong)
      else (numberString.toDouble * units.toNanos(1)).toLong
    } catch {
      case e: NumberFormatException => {
        throw new ConfigException.BadValue(
          "Could not parse duration number '" + numberString + "'")
      }
    }
  }

  def unicodeTrim(s: String) = s.trim()

  private def getUnits(s: String): String = {
    var i: Int = s.length - 1
    while (i >= 0) {
      val c: Char = s.charAt(i)
      if (!Character.isLetter(c)) return s.substring(i + 1)
      i -= 1
    }
    return s.substring(i + 1)
  }

  private val millis = Set("ms", "millis", "milliseconds")
  private val nanos = Set("ns", "nanos", "nanoseconds")
  def getMillisDuration(path: String) = {
    try {
      val res = parseDurationAsNanos(getString(path))

      Duration(res, NANOSECONDS)
    } catch {
      case err: Exception => null
    }
  }

  def getNanosDuration(path: String) = {
    val res = getString(path)
    val parts = res.split("[ \t]")
    assert(parts.size == 2 && (nanos contains parts(1)))
    Duration(parts(0).toInt, NANOSECONDS)
  }

}
