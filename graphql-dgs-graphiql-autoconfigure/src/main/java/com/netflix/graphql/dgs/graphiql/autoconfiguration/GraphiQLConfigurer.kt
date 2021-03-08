/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.graphiql.autoconfiguration

import com.netflix.graphql.dgs.graphiql.autoconfiguration.GraphiQLConfigurer.Constants.PATH_TO_GRAPHIQL_INDEX_HTML
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import org.springframework.web.servlet.resource.ResourceTransformer
import org.springframework.web.servlet.resource.ResourceTransformerChain
import org.springframework.web.servlet.resource.TransformedResource
import java.io.BufferedReader
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletRequest

@Configuration
@Suppress("SpringJavaInjectionPointsAutowiringInspection")
open class GraphiQLConfigurer(
    @Value("\${dgs.graphql.graphiql.path:/graphiql}") private val graphiqlPath: String,
    @Value("\${dgs.graphql.path:/graphql}") private val graphqlPath: String
) : WebMvcConfigurer {

    object Constants {
        const val PATH_TO_GRAPHIQL_INDEX_HTML = "/graphiql/index.html"
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController(graphiqlPath).setViewName("forward:$PATH_TO_GRAPHIQL_INDEX_HTML")
        registry.addViewController("$graphiqlPath/").setViewName("forward:$PATH_TO_GRAPHIQL_INDEX_HTML")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/graphiql/**")
            .addResourceLocations("classpath:/static/graphiql/")
            .setCachePeriod(3600)
            .resourceChain(true)
            .addResolver(PathResourceResolver())
            .addTransformer(TokenReplacingTransformer("<DGS_GRAPHQL_PATH>", graphqlPath))
    }

    class TokenReplacingTransformer(private val replaceToken: String, private val replaceValue: String) :
        ResourceTransformer {
        @Throws(IOException::class)

        override fun transform(
            request: HttpServletRequest?,
            resource: Resource,
            transformerChain: ResourceTransformerChain?
        ): Resource {
            if (request?.requestURI?.endsWith(PATH_TO_GRAPHIQL_INDEX_HTML) == true) {
                val content = resource.inputStream.bufferedReader().use(BufferedReader::readText)
                return TransformedResource(resource, content.replace(replaceToken, replaceValue).toByteArray(UTF_8))
            }
            return resource
        }
    }
}
