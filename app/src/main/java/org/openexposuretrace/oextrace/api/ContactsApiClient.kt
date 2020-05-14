package org.openexposuretrace.oextrace.api


class ContactsApiClient(private val client: ContactsApiEndpoint) : ContactsApiEndpoint by client
