package com.myrtle.middleware.controller;

import com.myrtle.middleware.model.Product;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.dtos.request.EditProduct;
import com.myrtle.middleware.dtos.request.ViewSingleProductResponse;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.products.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RestController
@RequestMapping(path = "api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/get-products")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getInvestmentProducts(@AuthenticationPrincipal UserDetails userDetails) {
        return productService.getInvestmentProducts();
    }

    public ResponseEntity<ApiResponse> deleteSettlements() {
        return productService.deleteAllSettlements();
    }

    @GetMapping("/all-products")
    public ResponseEntity<ApiResponse> getAllProducts(@AuthenticationPrincipal UserDetails userDetails) {
        return productService.getLocalProduct(userDetails.getUsername());
    }

    @GetMapping("/view-single-product/{productId}")
    public ResponseEntity<ApiResponse> viewSingleProduct(@PathVariable String productId,
                                                         @AuthenticationPrincipal UserDetails user) {
        if (user == null) {
            return new ResponseEntity<>(new ApiResponse(false, "Unauthorized", null), HttpStatus.UNAUTHORIZED);
        }

        ViewSingleProductResponse response = productService.viewSingleProducts(productId, user.getUsername());
        ApiResponse result = new ApiResponse(
                response.isSuccess(),
                response.message(),
                response
        );
        return new ResponseEntity<>(result, response.status());
    }

    public ResponseEntity<ApiResponse> deleteProduct(@PathVariable String id) {
        return productService.deleteProduct(id);
    }

}
