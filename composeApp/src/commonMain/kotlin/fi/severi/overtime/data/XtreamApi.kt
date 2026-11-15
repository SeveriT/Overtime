package fi.severi.overtime.data

import fi.severi.overtime.model.Channel
import fi.severi.overtime.model.Source
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Minimal Xtream Codes "player_api" client. Live channels only for now; VOD and series can be added later. */
class XtreamApi(private val client: HttpClient, private val json: Json) {

    suspend fun liveChannels(source: Source): List<Channel> {
        val categories = fetchArray(apiUrl(source, "get_live_categories"))
            .associate { it.str("category_id") to it.str("category_name") }
        return fetchArray(apiUrl(source, "get_live_streams")).mapNotNull { obj ->
            val streamId = obj.str("stream_id").ifEmpty { return@mapNotNull null }
            Channel(
                id = "${source.id}:$streamId",
                sourceId = source.id,
                name = obj.str("name"),
                group = categories[obj.str("category_id")] ?: "Other",
                url = "${base(source)}/live/${source.username.encodeURLParameter()}/${source.password.encodeURLParameter()}/$streamId.ts",
                logo = obj.str("stream_icon").takeIf { it.isNotBlank() },
                epgId = obj.str("epg_channel_id").takeIf { it.isNotBlank() },
            )
        }
    }

    fun epgUrl(source: Source): String =
        "${base(source)}/xmltv.php?username=${source.username.encodeURLParameter()}&password=${source.password.encodeURLParameter()}"

    private fun base(source: Source) = source.url.trim().trimEnd('/')

    private fun apiUrl(source: Source, action: String) =
        "${base(source)}/player_api.php?username=${source.username.encodeURLParameter()}" +
            "&password=${source.password.encodeURLParameter()}&action=$action"

    private suspend fun fetchArray(url: String): List<JsonObject> {
        val text = client.get(url).bodyAsText()
        val element = json.parseToJsonElement(text)
        if (element !is JsonArray) {
            val auth = (element as? JsonObject)?.get("user_info")?.jsonObject?.get("auth")?.jsonPrimitive?.content
            throw IllegalStateException(if (auth == "0") "Xtream login rejected" else "Unexpected Xtream response")
        }
        return element.mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.str(key: String): String = get(key)?.jsonPrimitive?.content ?: ""
}
