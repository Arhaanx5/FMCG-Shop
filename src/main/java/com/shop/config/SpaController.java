package com.shop.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller to forward all client-side React routes to index.html.
 * This prevents 404/403 errors when refreshing the page or navigating
 * directly to a sub-path in the Single Page Application.
 */
@Controller
public class SpaController {

    @RequestMapping(value = {
        "/",
        "/login",
        "/billing",
        "/products",
        "/customers",
        "/whatsapp",
        "/salesmen",
        "/stock",
        "/khata",
        "/expenses",
        "/damage",
        "/areas",
        "/deliveries",
        "/users"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
