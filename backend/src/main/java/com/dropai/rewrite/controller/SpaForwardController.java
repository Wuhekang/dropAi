package com.dropai.rewrite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {"/", "/dashboard", "/rewrite", "/login", "/new-project"})
    public String forward() {
        return "forward:/index.html";
    }
}
