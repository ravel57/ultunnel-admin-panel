package ru.ravel.ultunneladminpanel.model.sui

data class ObjUsersResponse(
	val clients: List<Client>,
	val config: SingBoxConfig,
	val enableTraffic: Boolean,
	val endpoints: List<Endpoint>?,
	val inbounds: List<InboundItem>,
	val onlines: Onlines,
	val outbounds: List<OutboundItem>,
	val services: List<Service>?,
	val subURI: String,
	val tls: List<TlsItem>
)