package com.datamaking.ctv

// --class com.datamaking.ctv.streaming_app_demo

import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.functions.udf

object streaming_app_demo {
  def main(args: Array[String]): Unit = {

    val Array(sourceTopic, sinkTopic, bootStrapServer, sourceStartingOffset, checkPointDir, geoIPDir) = args


    println("Spark Structured Streaming with Kafka Demo Application Started ...")

    val KAFKA_TOPIC_NAME_CONS = sourceTopic
    val KAFKA_OUTPUT_TOPIC_NAME_CONS = sinkTopic
    val KAFKA_BOOTSTRAP_SERVERS_CONS = bootStrapServer
    val KAFKA_STARTING_OFFSET = sourceStartingOffset
    val CHECK_POINT_DIR = checkPointDir
    val GEO_IP_DIR = geoIPDir

    System.setProperty("HADOOP_USER_NAME","hadoop")

    val spark = SparkSession.builder
      .appName("Spark Structured Streaming with Kafka Demo")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")

    // Stream from Kafka
    val web_detail_df = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS_CONS)
      .option("subscribe", KAFKA_TOPIC_NAME_CONS)
      .option("startingOffsets", KAFKA_STARTING_OFFSET)
      .load()

    println("Printing Schema of web_detail_df: ")
    web_detail_df.printSchema()

    println("From the complete kafka source schema just selecting the value and timestamp")
    val web_detail_df1 = web_detail_df.selectExpr("CAST(value AS STRING)", "CAST(timestamp AS TIMESTAMP)")

    println("Preparing the schema for the kafka value from source")
    val kafkaSourceSchema = StructType(Array(
     StructField("cust_id",LongType),
     StructField("public_ip",StringType),
     StructField("browser_id",IntegerType),
     StructField("device_id",IntegerType),
     StructField("event_timestamp",StringType),
     StructField("time_spent",StringType),
     StructField("webpage_name",StringType),
     StructField("url",StringType)))

    println("Parsing the kafka source value stream based on prepared schema for kafka value")
    val web_detail_df2 = web_detail_df1.select(from_json(col("value"), kafkaSourceSchema).as("web_detail"), col("timestamp"))

    println("Printing final kafka source prepared schema")
    val web_detail_df3 = web_detail_df2.select("web_detail.*", "timestamp")
    web_detail_df3.printSchema()

    println("Register the web_details kafka source dataframe as a temp view for easy SQL like operation")
    web_detail_df3.createOrReplaceTempView("web_detail_view")

    val web_detail_df4 = spark.sql(""" SELECT * from web_detail_view""")


    //--------------------------------------------------------------------------------------------------------
    val GeoIPschema = StructType(Array(
      StructField("ip_from",LongType,true),
      StructField("ip_to",LongType,true),
      StructField("country_code",StringType,true),
      StructField("country_name",StringType,true),
      StructField("region_name",StringType,true),
      StructField("city_name",StringType,true),
      StructField("latitude",DoubleType,true),
      StructField("longitude",DoubleType,true),
      StructField("zip_code",StringType,true)))

    println("Now using spark session with read for spark sql type operation on geoip data")
    val geoipDF_with_schema = spark.read.format("csv").option("header", "true").schema(GeoIPschema).load(GEO_IP_DIR)

    println("Print the geoip dataframe schema")
    geoipDF_with_schema.printSchema()

    println("Register the geoipDF_with_schema dataframe as a TempView so it becomes easy to do SQL like operations on DF")
    geoipDF_with_schema.createOrReplaceTempView("geoIpDF_view")

    val result = spark.sql(""" SELECT country_name, city_name, region_name FROM geoIpDF_view WHERE ip_from <= 16777217 AND 16777217 <= ip_to""")
    result.show()

    println("Now registering a temp udf for column level transformation operation")
    //val delimiter_remover = udf((x: String) => x.replace(".",""))
    val delimiter_remover = udf((x: String) => (x.replace(".","")).toLong  )
    spark.udf.register("delimiter_remover", delimiter_remover)

    //--------------------------------------------------------------------------------------------------------

    println("Now performing the join operation between two temp views one is of kafka source and the other of geoip data")
   // val web_detail_df5 = spark.sql("""SELECT K.cust_id, K.public_ip, delimiter_remover(K.public_ip) AS ip, K.browser_id, K.device_id, K.event_timestamp, K.time_spent, K.webpage_name, K.url, k.timestamp, G.country_code, G.country_name, G.region_name, G.city_name, G.zip_code, G.ip_from, G.ip_to FROM web_detail_view K INNER JOIN geoIpDF_view G WHERE K.public_ip >= G.ip_from AND K.public_ip <= G.ip_to""")
    val web_detail_df5 = spark.sql("""SELECT K.cust_id, K.public_ip, delimiter_remover(K.public_ip) AS ip, K.browser_id, K.device_id, K.event_timestamp, K.time_spent, K.webpage_name, K.url, k.timestamp, G.country_code, G.country_name, G.region_name, G.city_name, G.zip_code, G.ip_from, G.ip_to FROM web_detail_view K INNER JOIN geoIpDF_view G WHERE delimiter_remover(K.public_ip) >= G.ip_from AND delimiter_remover(K.public_ip) <= G.ip_to""")

    println("Printing Schema of final joined views dataframe web_detail_df5: ")
    web_detail_df5.printSchema()


    //--------------------------------------------------------------------------------------------------------

   /* val transaction_detail_df5 = transaction_detail_df4.withColumn("key", lit(100))
      .withColumn("value",
        concat(lit("{'transaction_card_type': '"), col("transaction_card_type"),
               lit("', 'total_transaction_amount: '"), col("total_transaction_amount").cast("string"),
               lit("'}")))
    */

    println("Now preparing the final schema of the dataframe which would be written to the kafka sink")
    val web_detail_df6 = web_detail_df5.withColumn("key", lit(100))
      .withColumn("value",
        concat(lit("{'cust_id': '"), col("cust_id"),
               lit("', 'public_ip': '"), col("public_ip").cast("string"),
               lit("', 'ip': '"), col("ip").cast("string"),
               lit("', 'browser_id': '"), col("browser_id").cast("string"),
               lit("', 'device_id': '"), col("device_id").cast("string"),
               lit("', 'event_timestamp': '"), col("event_timestamp").cast("string"),
               lit("', 'time_spent': '"), col("time_spent").cast("string"),
               lit("', 'webpage_name': '"), col("webpage_name").cast("string"),
               lit("', 'url': '"), col("url").cast("string"),
               lit("', 'timestamp': '"), col("timestamp").cast("string"),
               lit("', 'country_code': '"), col("country_code").cast("string"),
               lit("', 'country_name': '"), col("country_name").cast("string"),
               lit("', 'region_name': '"), col("region_name").cast("string"),
               lit("', 'city_name': '"), col("city_name").cast("string"),
               lit("', 'zip_code': '"), col("zip_code").cast("string"),
               lit("', 'ip_from': '"), col("ip_from").cast("string"),
               lit("', 'ip_to': '"), col("ip_to").cast("string"),
               lit("'}")))

    println("Now printing the final schema of the dataframe which would be written to the kafka sink")
    web_detail_df6.printSchema()
    //---------------------------------------------------------------------------------------------------------

    //Printing the web_detail_df4 on the console
    val web_detail_write_stream = web_detail_df6
      .writeStream
      .trigger(Trigger.ProcessingTime("1 seconds"))
      .outputMode("update")
      .option("truncate","false")
      .format("console")
      .start()

    // Write final result in the Kafka topic as key, value
    val web_detail_write_stream_1 = web_detail_df6
      .selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS_CONS)
      .option("topic", KAFKA_OUTPUT_TOPIC_NAME_CONS)
      .trigger(Trigger.ProcessingTime("1 seconds"))
      .outputMode("update")
      .option("checkpointLocation", CHECK_POINT_DIR)
      .start()

    web_detail_write_stream.awaitTermination()


  }
}
