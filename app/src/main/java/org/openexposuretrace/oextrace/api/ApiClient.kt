package org.openexposuretrace.oextrace.api


class ApiClient(private val client: ApiEndpoint) : ApiEndpoint by client
