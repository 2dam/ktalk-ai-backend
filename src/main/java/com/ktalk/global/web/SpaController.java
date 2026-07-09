package com.ktalk.global.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping("/oauth2/redirect")
    public String oauthRedirect() {
        return "forward:/index.html";
    }
}
