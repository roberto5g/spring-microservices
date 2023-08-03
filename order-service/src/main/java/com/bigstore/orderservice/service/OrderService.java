package com.bigstore.orderservice.service;

import com.bigstore.orderservice.dto.InventoryResponse;
import com.bigstore.orderservice.dto.OrderLineItemsDto;
import com.bigstore.orderservice.dto.OrderRequest;

import com.bigstore.orderservice.model.Order;
import com.bigstore.orderservice.model.OrderLineItens;
import com.bigstore.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public void placeOrder(OrderRequest orderRequest){
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItens> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream().map(this::mapToDto)
                .toList();

        order.setOrderLineItens(orderLineItems);

        List<String> skuCodes = order.getOrderLineItens()
                .stream()
                .map(OrderLineItens::getSkuCode)
                .toList();

        // Call Inventory service, and place order if product is in stock
        InventoryResponse[] inventoryResponsesArrays = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory", uriBuilder -> uriBuilder
                        .queryParam("skuCode", skuCodes)
                        .build()
                )
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductsInStock = Arrays.stream(inventoryResponsesArrays).allMatch(InventoryResponse::isInStock);

        if (Boolean.TRUE.equals(allProductsInStock)){
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }
    }

    private OrderLineItens mapToDto(OrderLineItemsDto orderLineItensDto) {
        OrderLineItens orderLineItens = new OrderLineItens();
        orderLineItens.setPrice(orderLineItensDto.getPrice());
        orderLineItens.setQuantity(orderLineItensDto.getQuantity());
        orderLineItens.setSkuCode(orderLineItensDto.getSkuCode());
        return orderLineItens;
    }
}
