package tic.kotlin.learning.club

import aws.sdk.kotlin.services.dynamodb.*
import aws.sdk.kotlin.services.dynamodb.endpoints.*
import aws.sdk.kotlin.services.dynamodb.model.*
import aws.sdk.kotlin.services.dynamodb.paginators.*
import aws.sdk.kotlin.services.dynamodb.waiters.*
import aws.sdk.kotlin.services.lambda.*
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import java.util.UUID
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlinx.coroutines.runBlocking

import com.google.gson.Gson
import com.google.gson.JsonParser

// https://sdk.amazonaws.com/kotlin/api/latest/dynamodb/index.html
// https://docs.aws.amazon.com/ja_jp/sdk-for-kotlin/latest/developer-guide/kotlin_dynamodb_code_examples.html

/*

テーブル構成は以下の通りです

tableName: kotlin-learning

id(primarykey): String
name: String
age(省略可): Int



以下のプログラムを作成します
User型のデータをDynamoに入れる際の型変換にはtoMapとtoAttributeValueMapを使用してください

1
  id = UUID
  name = 自分の名前
  age = 自分の年齢
  として、テーブルにput
  メソッド名はaddUserとします
  なお、id, name, ageは引数で受け取るようにしてください. User型で受け取っても構いません

2
  テーブルの中身を全件取得して表示
  メソッド名はscanAllとします

3
  idが1で生成したidのユーザーを検索して表示
  メソッド名はsearchByKeyとします
  なお、idは引数で受け取るようにしてください

4
  ageが20歳以上のユーザーを検索して表示
  メソッド名はsearchByAgeとします
  なお、ageは引数で受け取るようにしてください

以上の4つのメソッドをAppクラス内に作成し、AppクラスのhandleRequestメソッド内で順番に呼び出してください

*/

// ユーザーの型. id, nameは必須, ageは任意
data class User(val id: String, val name: String, val age: Int?)

// AWSのリージョン
val REGION = "us-east-1"

// テーブル名
val TABLE_NAME = "kotlin-learning"

// インスタンス化
val utils = Utils()

class App : RequestHandler<Map<String, String>, String> {
  override fun handleRequest(event: Map<String, String>?, context: Context?): String{
    val res = runBlocking {
    if (event == null) {throw Exception("event is null")}
    // ユーザーの情報を設定
    val id = UUID.randomUUID().toString()
    val name = if (event["name"] != null) {event["name"]!!} else {throw Exception("name is null")}
    val age = if (event["age"] != null) {event["age"]!!.toInt()} else {throw Exception("age is null")}
    val user = User(id, name, age)

    println("ユーザーを追加")
    addUser(user)
    println("追加完了\n")

    println("全件取得")
    scanAll()
    println("取得完了\n")

    println("id検索")
    searchByKey(id)
    println("検索完了\n")

    println("年齢で絞り込み")
    searchByAge(20)
    println("取得完了\n")
    user
    }
    return Gson().toJson(res)
  }

  suspend fun addUser(user: User) {
    // 型変換
    val itemValues = utils.toAttributeValueMap(utils.toMap(user))
    // テーブル名とitemを指定
    val req = PutItemRequest {
      tableName = TABLE_NAME
      item = itemValues
    }
    // 追加
    DynamoDbClient { region = REGION }.use { ddb -> ddb.putItem(req) }
  }

  suspend fun scanAll() {
    DynamoDbClient { region = REGION }.use { ddb ->
      // テーブル名を指定
      val request = ScanRequest { tableName = TABLE_NAME }
      // 全件取得
      val response = ddb.scan(request)
      // 取得結果を出力
      response.items?.forEach { item ->
        item.forEach { mp ->
          println("key = ${mp.key}")
          println("value = ${mp.value}")
        }
      }
    }
  }

  suspend fun searchByKey(id: String) {
    // idとnameをMapにセット
    val keys = mutableMapOf<String, Any>()
    keys["id"] = id

    val keyToGet = utils.toAttributeValueMap(keys)
    // テーブル名とキーを設定
    val req = GetItemRequest {
      key = keyToGet
      tableName = TABLE_NAME
    }

    DynamoDbClient { region = REGION }.use { ddb ->
      // 取得
      val response = ddb.getItem(req)
      // 取得結果を出力
      response.item?.forEach { mp ->
        println("key = ${mp.key}")
        println("value = ${mp.value}")
      }
    }
  }

  suspend fun searchByAge(age: Int) {
    DynamoDbClient { region = REGION }.use { ddb ->
      // テーブル名, 検索条件を指定
      val query = ScanRequest {
        tableName = TABLE_NAME
        // 条件式を記述. :ageの部分は変数として解釈され, expressionAttributeValuesでセットした値が使用されます
        filterExpression = "age >= :age"
        // :ageの値をセット
        expressionAttributeValues = utils.toAttributeValueMap(mapOf(":age" to age))
      }
      // 取得
      val response = ddb.scan(query)
      // 取得結果を出力
      response.items?.forEach { item ->
        item.forEach { mp ->
          println("key = ${mp.key}")
          println("value = ${mp.value}")
        }
      }
    }
  }
}

fun main() {
  // handlerを起動
  val app = App()
  app.handleRequest(null, null)
}