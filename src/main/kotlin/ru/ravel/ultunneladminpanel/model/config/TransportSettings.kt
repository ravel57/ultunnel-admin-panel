package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TransportSettings(
	@JsonProperty("type")
	val type: String = "xhttp",

	@JsonProperty("host")
	val host: String? = null,

	@JsonProperty("path")
	val path: String = "/",

	@JsonProperty("mode")
	val mode: String = "auto",

	@JsonProperty("http_version")
	val httpVersion: String? = null,

	@JsonProperty("no_grpc_header")
	val noGrpcHeader: Boolean? = null,

	@JsonProperty("no_sse_header")
	val noSseHeader: Boolean? = null,

	@JsonProperty("max_post_size")
	val maxPostSize: Int? = null,

	@JsonProperty("max_buffered_posts")
	val maxBufferedPosts: Int? = null,
)