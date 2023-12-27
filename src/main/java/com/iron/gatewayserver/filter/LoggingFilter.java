package com.iron.gatewayserver.filter;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


/*
	WebFilter : Spring에서 제공하는 인터셉터 기능을 대신 제공
	Mono : 데이터 전송처리를 위해 사용
	ServerWebExchange : HTTP 요청과 응답에 대한 액세스를 제공
	WebFilterChain : 다음 필터에 항목을 전달하기 위해 사용
	decoratedRequest : request 객체에 존재하는 요청 데이터를 스트리밍하기 위해 ServerHttpRequestDecorator 클래스를 통해 기능 재정의가 된 객체
		-> 실제로 요청 데이터를 다음 filter나 마이크로 서비스에 전달할 때 request로 들어온 json 데이터를 출력하는 부분을 decoratedrequest가 재정의된 기능에 개발해야 함
	decoratedResponse: response 객체에 존재하는 응답 데이터를 읽어오기 위해 ServerHttpResponseDecorator 클래스를 통해 기능 재정의가 된 객체
		-> response json 데이터 출력기능은 decoratedresponse가 재정의된 기능에 개발해야 함
		
		
 */
@Component
@Slf4j
public class LoggingFilter implements WebFilter {

	@Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        ServerHttpRequest request = exchange.getRequest();
        DataBufferFactory dataBufferFactory = response.bufferFactory();

        // log the request body
        ServerHttpRequest decoratedRequest = getDecoratedRequest(request);
        // log the response body
        ServerHttpResponseDecorator decoratedResponse = getDecoratedResponse(response, request, dataBufferFactory);
        return chain.filter(exchange.mutate().request(decoratedRequest).response(decoratedResponse).build());
    }

	private ServerHttpResponseDecorator getDecoratedResponse(ServerHttpResponse response, ServerHttpRequest request, DataBufferFactory dataBufferFactory) {
        return new ServerHttpResponseDecorator(response) {
            @Override
            public Mono<Void> writeWith(final Publisher<? extends DataBuffer> body) {

                if (body instanceof Flux) {

                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {

                        DefaultDataBuffer joinedBuffers = new DefaultDataBufferFactory().join(dataBuffers);
                        byte[] content = new byte[joinedBuffers.readableByteCount()];
                        joinedBuffers.read(content);
                        String responseBody = new String(content, StandardCharsets.UTF_8); //MODIFY RESPONSE and Return the Modified response
                        log.info("request.id: {}, method: {}, url: {}, \nresponse body :{}", request.getId(), request.getMethod(), request.getURI(), responseBody);

                        return dataBufferFactory.wrap(responseBody.getBytes());
                    })
                    .switchIfEmpty(Flux.defer(() -> {

                        System.out.println("If empty");
                        return Flux.just();
                    }))
                    ).onErrorResume(err -> {
                        log.error("error while decorating Response: {}", err.getMessage());
                        return Mono.empty();
                    });

                } else {
                    System.out.println("Not Flux");
                }
                return super.writeWith(body);
            }
        };
    }

    private ServerHttpRequest getDecoratedRequest(ServerHttpRequest request) {

        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {

                log.info("request.id: {}, method: {} , url: {}", request.getId(), request.getMethod(), request.getURI());
                return super.getBody().publishOn(Schedulers.boundedElastic()).doOnNext(dataBuffer -> {
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        Channels.newChannel(baos).write(dataBuffer.asByteBuffer().asReadOnlyBuffer());
                        String body = baos.toString(StandardCharsets.UTF_8);
                        log.info("request.id: {}, request body :{}", request.getId(), body);
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                });
            }
        };
    }

}