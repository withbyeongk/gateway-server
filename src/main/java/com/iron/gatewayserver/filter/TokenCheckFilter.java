package com.iron.gatewayserver.filter;

import com.iron.gatewayserver.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TokenCheckFilter implements WebFilter {

	private final AccountService accountService;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

		ServerHttpRequest request = exchange.getRequest();

		boolean success = false;

		Object tmpToken = request.getHeaders().get("token");
		Object tmpAccountId = request.getHeaders().get("accountId");
		String token = "";
		String accountId = "";

		if (tmpToken != null) {
			token = tmpToken.toString().replace("[", "").replace("]", "");
		}
		if (tmpAccountId != null) {
			accountId = tmpAccountId.toString().replace("[", "").replace("]", "");
		}

		log.info("token = {}", token);

		success = accountService.existsByAccountIdAndToken(accountId, token);

		log.info("user authentication check result = {}", success);

		if (!success) {
			return errorResponse(exchange);
		}

		return chain.filter(exchange);
	}

	/**
	 * 에러 메시지 응답 전달
	 *
	 * @param exchange
	 * @return
	 */
	private Mono<Void> errorResponse(ServerWebExchange exchange) {
		ServerHttpResponse response = exchange.getResponse();
		Gson gson = new Gson();

		response.setStatusCode(HttpStatus.UNAUTHORIZED);

		Map<String, String> paramMap = new HashMap<>();
		paramMap.put("code", "401");
		paramMap.put("message", "Unauthorized token");

		String json = gson.toJson(paramMap);

		// response log db 저장 필요

		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		response.setStatusCode(HttpStatus.UNAUTHORIZED);

		return response.writeWith(Mono.just(buffer));
	}
}
