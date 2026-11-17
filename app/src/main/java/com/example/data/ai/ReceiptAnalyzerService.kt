package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.ParsedReceiptItem
import com.example.data.model.ParsedReceiptResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "ReceiptAnalyzer"

class ReceiptAnalyzerService {

    private val receiptSchema = Schema.obj(
        properties = mapOf(
            "supplierName" to Schema.string(description = "거래처 또는 도매상 상호명"),
            "dateKey" to Schema.string(description = "거래일자 YYYY-MM-DD 형식 (없으면 빈 문자열)"),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "productName" to Schema.string(description = "제품명 또는 품목명"),
                        "quantity" to Schema.integer(description = "수량 (정수)"),
                        "unitCost" to Schema.integer(description = "단가 원화 정수"),
                        "totalAmount" to Schema.integer(description = "금액 원화 정수")
                    ),
                    optionalProperties = listOf("totalAmount")
                ),
                description = "명세서에서 인식된 사입 품목 목록"
            )
        ),
        optionalProperties = listOf("supplierName", "dateKey")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = receiptSchema
            }
        )
    }

    suspend fun analyzeReceiptImage(
        context: Context,
        imageUri: Uri,
        fallbackDateKey: String
    ): Result<ParsedReceiptResult> {
        return try {
            val bitmap = withContext(Dispatchers.IO) {
                decodeAndScaleBitmap(context, imageUri, maxDimension = 1600)
            } ?: return Result.failure(IllegalArgumentException("이미지를 불러올 수 없습니다."))

            val prompt = """
                이 거래명세서 또는 영수증 이미지를 분석하여 인형뽑기 경품 사입 품목을 정확하게 추출해 주세요.
                - productName: 제품명(한글/영문 그대로 깔끔하게 정리)
                - quantity: 수량(정수, 미표기 시 1)
                - unitCost: 개당 단가(원 단위 정수). 단가가 없고 합계 금액과 수량만 있으면 합계/수량으로 계산
                - totalAmount: 품목별 금액(원 단위 정수)
                - supplierName: 공급처/도매상 이름 (없으면 빈 문자열)
                - dateKey: 거래 날짜 YYYY-MM-DD (인식 불가 시 빈 문자열)
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }

            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("AI 분석 결과가 비어 있습니다."))

            val root = JSONObject(jsonText)
            val supplierName = root.optString("supplierName", "").trim()
            val rawDate = root.optString("dateKey", "").trim()
            val resolvedDate = if (rawDate.matches("^\\d{4}-\\d{2}-\\d{2}$".toRegex())) {
                rawDate
            } else {
                fallbackDateKey
            }

            val itemsArray = root.optJSONArray("items")
            val parsedItems = buildList {
                if (itemsArray != null) {
                    for (i in 0 until itemsArray.length()) {
                        val obj = itemsArray.getJSONObject(i)
                        val name = obj.optString("productName", "").trim()
                        if (name.isBlank()) continue
                        val qty = obj.optLong("quantity", 1L).coerceAtLeast(1L)
                        var cost = obj.optLong("unitCost", 0L).coerceAtLeast(0L)
                        var total = obj.optLong("totalAmount", qty * cost).coerceAtLeast(0L)
                        if (cost == 0L && total > 0L && qty > 0L) {
                            cost = total / qty
                        } else if (total == 0L && cost > 0L) {
                            total = qty * cost
                        }
                        add(
                            ParsedReceiptItem(
                                id = UUID.randomUUID().toString(),
                                productName = name,
                                quantity = qty,
                                unitCost = cost,
                                totalAmount = total
                            )
                        )
                    }
                }
            }

            if (parsedItems.isEmpty()) {
                return Result.failure(IllegalStateException("명세서에서 품목을 인식하지 못했습니다. 선명한 사진으로 다시 시도해 주세요."))
            }

            Result.success(
                ParsedReceiptResult(
                    supplierName = supplierName,
                    dateKey = resolvedDate,
                    items = parsedItems
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Receipt analysis timed out", e)
            Result.failure(Exception("명세서 분석 시간이 초과되었습니다(60초). 다시 시도해 주세요."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Receipt analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "명세서 분석 중 오류가 발생했습니다."))
        }
    }

    private fun decodeAndScaleBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = maxOf(width, height).toFloat() / maxDimension.toFloat()
                if (scale > 1f) {
                    decoder.setTargetSize(
                        (width / scale).toInt().coerceAtLeast(1),
                        (height / scale).toInt().coerceAtLeast(1)
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } ?: return null

        val maxSide = maxOf(original.width, original.height)
        if (maxSide <= maxDimension) return original
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        return Bitmap.createScaledBitmap(
            original,
            (original.width * ratio).toInt().coerceAtLeast(1),
            (original.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
