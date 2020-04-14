/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.catalyst.expressions

import java.time.Instant

import scala.language.implicitConversions

import org.apache.spark.SparkFunSuite
import org.apache.spark.sql.catalyst.util.DateTimeConstants._
import org.apache.spark.sql.catalyst.util.DateTimeTestUtils
import org.apache.spark.sql.catalyst.util.IntervalUtils.{safeStringToInterval, stringToInterval}
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.types.Decimal
import org.apache.spark.unsafe.types.{CalendarInterval, UTF8String}

class IntervalExpressionsSuite extends SparkFunSuite with ExpressionEvalHelper {
  implicit def stringToUTF8Str(str: String): UTF8String = UTF8String.fromString(str)

  implicit def interval(s: String): Literal = {
    Literal(stringToInterval( "interval " + s))
  }

  test("millenniums") {
    checkEvaluation(ExtractIntervalMillenniums("0 years"), 0)
    checkEvaluation(ExtractIntervalMillenniums("9999 years"), 9)
    checkEvaluation(ExtractIntervalMillenniums("1000 years"), 1)
    checkEvaluation(ExtractIntervalMillenniums("-2000 years"), -2)
    // Microseconds part must not be taken into account
    checkEvaluation(ExtractIntervalMillenniums("999 years 400 days"), 0)
    // Millennium must be taken from years and months
    checkEvaluation(ExtractIntervalMillenniums("999 years 12 months"), 1)
    checkEvaluation(ExtractIntervalMillenniums("1000 years -1 months"), 0)
  }

  test("centuries") {
    checkEvaluation(ExtractIntervalCenturies("0 years"), 0)
    checkEvaluation(ExtractIntervalCenturies("9999 years"), 99)
    checkEvaluation(ExtractIntervalCenturies("1000 years"), 10)
    checkEvaluation(ExtractIntervalCenturies("-2000 years"), -20)
    // Microseconds part must not be taken into account
    checkEvaluation(ExtractIntervalCenturies("99 years 400 days"), 0)
    // Century must be taken from years and months
    checkEvaluation(ExtractIntervalCenturies("99 years 12 months"), 1)
    checkEvaluation(ExtractIntervalCenturies("100 years -1 months"), 0)
  }

  test("decades") {
    checkEvaluation(ExtractIntervalDecades("0 years"), 0)
    checkEvaluation(ExtractIntervalDecades("9999 years"), 999)
    checkEvaluation(ExtractIntervalDecades("1000 years"), 100)
    checkEvaluation(ExtractIntervalDecades("-2000 years"), -200)
    // Microseconds part must not be taken into account
    checkEvaluation(ExtractIntervalDecades("9 years 400 days"), 0)
    // Decade must be taken from years and months
    checkEvaluation(ExtractIntervalDecades("9 years 12 months"), 1)
    checkEvaluation(ExtractIntervalDecades("10 years -1 months"), 0)
  }

  test("years") {
    checkEvaluation(ExtractIntervalYears("0 years"), 0)
    checkEvaluation(ExtractIntervalYears("9999 years"), 9999)
    checkEvaluation(ExtractIntervalYears("1000 years"), 1000)
    checkEvaluation(ExtractIntervalYears("-2000 years"), -2000)
    // Microseconds part must not be taken into account
    checkEvaluation(ExtractIntervalYears("9 years 400 days"), 9)
    // Year must be taken from years and months
    checkEvaluation(ExtractIntervalYears("9 years 12 months"), 10)
    checkEvaluation(ExtractIntervalYears("10 years -1 months"), 9)
  }

  test("quarters") {
    checkEvaluation(ExtractIntervalQuarters("0 months"), 0)
    checkEvaluation(ExtractIntervalQuarters("1 months"), 0)
    checkEvaluation(ExtractIntervalQuarters("-1 months"), 0)
    checkEvaluation(ExtractIntervalQuarters("2 months"), 0)
    checkEvaluation(ExtractIntervalQuarters("-2 months"), 0)
    checkEvaluation(ExtractIntervalQuarters("1 years -1 months"), 3)
    checkEvaluation(ExtractIntervalQuarters("-1 years 1 months"), -3)
    checkEvaluation(ExtractIntervalQuarters("2 years 3 months"), 9)
    checkEvaluation(ExtractIntervalQuarters("-2 years -3 months"), -9)
    checkEvaluation(ExtractIntervalQuarters("9999 years"), 39996)
  }

  test("months") {
    checkEvaluation(ExtractIntervalMonths("0 year"), 0)
    for (m <- -24 to 24) {
      checkEvaluation(ExtractIntervalMonths(s"$m months"), m)
    }
    checkEvaluation(ExtractIntervalMonths("1 year 10 months"), 22)
    checkEvaluation(ExtractIntervalMonths("-2 year -10 months"), -34)
    checkEvaluation(ExtractIntervalMonths("9999 years"), 119988)
  }

  private val largeInterval: String = "9999 years 11 months " +
    "31 days 11 hours 59 minutes 59 seconds 999 milliseconds 999 microseconds"

  test("days") {
    val start = Instant.parse("2019-01-01T00:00:00.000000Z")
    val end = Instant.parse("2019-01-15T00:00:00.000000Z")

    checkEvaluation(ExtractIntervalDays("0 days"), 0)
    checkEvaluation(ExtractIntervalDays("1 days 100 seconds"), 1)
    checkEvaluation(ExtractIntervalDays("-1 days -100 seconds"), -1)
    checkEvaluation(ExtractIntervalDays("-365 days"), -365)
    checkEvaluation(ExtractIntervalDays("365 days"), 365)
    checkEvaluation(ExtractIntervalDays("100 year 10 months 5 days"), 36305)
    checkEvaluation(ExtractIntervalDays(largeInterval), 3600001)
    checkEvaluation(ExtractIntervalDays(SubtractTimestamps(Literal(end), Literal(start))), 14)
  }

  test("hours") {
    checkEvaluation(ExtractIntervalHours("0 hours"), 0L)
    checkEvaluation(ExtractIntervalHours("1 hour"), 1L)
    checkEvaluation(ExtractIntervalHours("-1 hour"), -1L)
    checkEvaluation(ExtractIntervalHours("23 hours"), 23L)
    checkEvaluation(ExtractIntervalHours("-23 hours"), -23L)
    checkEvaluation(ExtractIntervalHours("100 year 10 months 10 days 10 hours"), 871450L)
    checkEvaluation(ExtractIntervalHours("10 hours 100 minutes"), 11L)
    checkEvaluation(ExtractIntervalHours(largeInterval), 86400035L)
  }

  test("minutes") {
    checkEvaluation(ExtractIntervalMinutes("0 minute"), 0L)
    checkEvaluation(ExtractIntervalMinutes("1 minute"), 1L)
    checkEvaluation(ExtractIntervalMinutes("-1 minute"), -1L)
    checkEvaluation(ExtractIntervalMinutes("59 minute"), 59L)
    checkEvaluation(ExtractIntervalMinutes("-59 minute"), -59L)
    checkEvaluation(ExtractIntervalMinutes("100 year 10 months 10 minutes"), 52272010L)
    checkEvaluation(ExtractIntervalMinutes(largeInterval), 5184002159L)
  }

  test("seconds") {
    checkEvaluation(ExtractIntervalSeconds("0 second"), Decimal(0, 18, 6))
    checkEvaluation(ExtractIntervalSeconds("1 second"), Decimal(1.0, 18, 6))
    checkEvaluation(ExtractIntervalSeconds("-1 second"), Decimal(-1.0, 18, 6))
    checkEvaluation(ExtractIntervalSeconds("1 minute 59 second"), Decimal(119.0, 18, 6))
    checkEvaluation(ExtractIntervalSeconds("-59 minutes -59 seconds"), Decimal(-3599.0, 18, 6))
    checkEvaluation(
      ExtractIntervalSeconds("100 year 10 months 10 seconds"), Decimal(3136320010.0, 18, 6))
    checkEvaluation(ExtractIntervalSeconds(largeInterval), Decimal(311040129599999999L, 18, 6))
    checkEvaluation(
      ExtractIntervalSeconds("10 seconds 1 milliseconds 1 microseconds"),
      Decimal(10001001, 18, 6))
    checkEvaluation(ExtractIntervalSeconds("61 seconds 1 microseconds"), Decimal(61.000001, 18, 6))
  }

  test("milliseconds") {
    checkEvaluation(ExtractIntervalMilliseconds("0 milliseconds"), Decimal(0, 18, 3))
    checkEvaluation(ExtractIntervalMilliseconds("1 milliseconds"), Decimal(1.0, 18, 3))
    checkEvaluation(ExtractIntervalMilliseconds("-1 milliseconds"), Decimal(-1.0, 18, 3))
    checkEvaluation(
      ExtractIntervalMilliseconds("1 second 999 milliseconds"),
      Decimal(1999.0, 18, 3))
    checkEvaluation(
      ExtractIntervalMilliseconds("999 milliseconds 1 microsecond"),
      Decimal(999.001, 18, 3))
    checkEvaluation(
      ExtractIntervalMilliseconds("-1 second -999 milliseconds"),
      Decimal(-1999.0, 18, 3))
    checkEvaluation(
      ExtractIntervalMilliseconds("100 year 1 millisecond"), Decimal(3110400000001.000, 18, 3))
    checkEvaluation(ExtractIntervalMilliseconds(largeInterval), Decimal(311040129599999999L, 18, 3))
  }

  test("microseconds") {
    checkEvaluation(ExtractIntervalMicroseconds("0 microseconds"), 0L)
    checkEvaluation(ExtractIntervalMicroseconds("1 microseconds"), 1L)
    checkEvaluation(ExtractIntervalMicroseconds("-1 microseconds"), -1L)
    checkEvaluation(ExtractIntervalMicroseconds("1 second 999 microseconds"), 1000999L)
    checkEvaluation(ExtractIntervalMicroseconds("999 milliseconds 1 microseconds"), 999001L)
    checkEvaluation(ExtractIntervalMicroseconds("-1 second -999 microseconds"), -1000999L)
    checkEvaluation(ExtractIntervalMicroseconds("11 year 1 microseconds"), 342144000000001L)
    checkEvaluation(ExtractIntervalMicroseconds(largeInterval), 311040129599999999L)
  }

  test("epoch") {
    checkEvaluation(ExtractIntervalEpoch("0 months"), Decimal(0.0, 18, 6))
    checkEvaluation(ExtractIntervalEpoch("10000 years"), Decimal(311040000000.0, 18, 6))
    checkEvaluation(ExtractIntervalEpoch("1 year"), Decimal(31104000.0, 18, 6))
    checkEvaluation(ExtractIntervalEpoch("-1 year"), Decimal(-31104000.0, 18, 6))
    checkEvaluation(
      ExtractIntervalEpoch("1 second 1 millisecond 1 microsecond"),
      Decimal(1.001001, 18, 6))
  }

  test("multiply") {
    def check(
        interval: String,
        num: Double,
        expected: String,
        isAnsi: Option[Boolean] = None): Unit = {
      val expectedRes = safeStringToInterval(expected)
      val configs = if (isAnsi.isEmpty) Seq("true", "false") else isAnsi.map(_.toString).toSeq
      configs.foreach { v =>
        withSQLConf(SQLConf.ANSI_ENABLED.key -> v) {
          val expr = MultiplyInterval(Literal(stringToInterval(interval)), Literal(num))
          if (expectedRes == null) {
            checkExceptionInExpression[ArithmeticException](expr, expected)
          } else {
            checkEvaluation(expr, expectedRes)
          }
        }
      }
    }

    check("0 seconds", 10, "0 seconds")
    check("10 hours", 0, "0 hours")
    check("12 months 1 microseconds", 2, "2 years 2 microseconds")
    check("-5 year 3 seconds", 3, "-15 years 9 seconds")
    check("1 year 1 second", 0.5, "6 months 500 milliseconds")
    check("-100 years -1 millisecond", 0.5, "-50 years -500 microseconds")
    check("2 months 4 seconds", -0.5, "-1 months -2 seconds")
    check("1 month 2 microseconds", 1.5, "1 months 3 microseconds")
    check("2 months", Int.MaxValue, "integer overflow", Some(true))
    check("2 months", Int.MaxValue, Int.MaxValue + " months", Some(false))
  }

  test("divide") {
    def check(
        interval: String,
        num: Double,
        expected: String,
        isAnsi: Option[Boolean] = None): Unit = {
      val expectedRes = safeStringToInterval(expected)
      val configs = if (isAnsi.isEmpty) Seq("true", "false") else isAnsi.map(_.toString).toSeq
      configs.foreach { v =>
        withSQLConf(SQLConf.ANSI_ENABLED.key -> v) {
          val expr = DivideInterval(Literal(stringToInterval(interval)), Literal(num))
          if (expected != null && expectedRes == null) {
            checkExceptionInExpression[ArithmeticException](expr, expected)
          } else {
            checkEvaluation(expr, expectedRes)
          }
        }
      }
    }

    check("0 seconds", 10, "0 seconds")
    check("12 months 3 milliseconds", 2, "6 months 0.0015 seconds")
    check("-5 year 3 seconds", 3, "-1 years -8 months 1 seconds")
    check("6 years -7 seconds", 3, "2 years -2.333333 seconds")
    check("2 years -8 seconds", 0.5, "4 years -16 seconds")
    check("-1 month 2 microseconds", -0.25, "4 months -8 microseconds")
    check("1 month 3 microsecond", 1.5, "2 microseconds")
    check("1 second", 0, "divide by zero", Some(true))
    check("1 second", 0, null, Some(false))
    check(s"${Int.MaxValue} months", 0.9, "integer overflow", Some(true))
    check(s"${Int.MaxValue} months", 0.9, Int.MaxValue + " months", Some(false))
  }

  test("make interval") {
    def check(
        years: Int = 0,
        months: Int = 0,
        weeks: Int = 0,
        days: Int = 0,
        hours: Int = 0,
        minutes: Int = 0,
        seconds: Int = 0,
        millis: Int = 0,
        micros: Int = 0): Unit = {
      val secFrac = DateTimeTestUtils.secFrac(seconds, millis, micros)
      val intervalExpr = MakeInterval(Literal(years), Literal(months), Literal(weeks),
        Literal(days), Literal(hours), Literal(minutes), Literal(Decimal(secFrac, 8, 6)))
      val totalMonths = years * MONTHS_PER_YEAR + months
      val totalDays = weeks * DAYS_PER_WEEK + days
      val totalMicros = secFrac + minutes * MICROS_PER_MINUTE + hours * MICROS_PER_HOUR
      val expected = new CalendarInterval(totalMonths, totalDays, totalMicros)
      checkEvaluation(intervalExpr, expected)
    }

    check(months = 0, days = 0, micros = 0)
    check(years = -123)
    check(weeks = 123)
    check(millis = -123)
    check(9999, 11, 0, 31, 23, 59, 59, 999, 999)
    check(years = 10000, micros = -1)
    check(-9999, -11, 0, -31, -23, -59, -59, -999, -999)
    check(years = -10000, micros = 1)
  }
}
