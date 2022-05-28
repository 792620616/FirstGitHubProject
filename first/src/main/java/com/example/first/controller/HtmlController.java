package com.example.first.controller;


import io.swagger.annotations.Api;
import lombok.extern.log4j.Log4j2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Log4j2
@Controller
@Api("html")
public class HtmlController {
    Logger logger = LoggerFactory.getLogger(HtmlController.class);
    @GetMapping("/index")
    public String index(){
        logger.error("11111");
        logger.debug("222222");
        logger.info("44444");
       log.error("qwqw");
        return "index";
    }
}
