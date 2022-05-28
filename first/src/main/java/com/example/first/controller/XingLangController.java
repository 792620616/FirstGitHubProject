package com.example.first.controller;

import com.example.first.service.feign.FeignTimeOut;
import com.example.first.service.feign.FeignTimeOutSingle;
import com.example.first.service.xinlang.XingLangService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@Api(tags = "第一个")
@RestController
public class XingLangController {

    @Resource
    private XingLangService xingLangService;

    @Resource
    private FeignTimeOut feignTimeOut;

    @Resource
    private FeignTimeOutSingle feignTimeOutSingle;
    Logger logger = LoggerFactory.getLogger(XingLangController.class);

    @ApiOperation(value = "上传文件接口",notes = "上传文件接口")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "上传人")
    })
    @GetMapping("/xingLang")
    public Map xingLang(String id, Integer date){
        Map data = xingLangService.getXingLang(id, 120);
        return data;
    }
    @GetMapping("/timeOut")
    public void timeOut(String id, Integer date){
        String timeOut = feignTimeOut.getTimeOut();
        logger.error("11111");
        logger.debug("222222");
        logger.info("2121");
        logger.info("44444");
        System.out.println(timeOut);
    }
    @GetMapping("/timeOutSingle")
    public void timeOutSingle(String id, Integer date){
        String timeOut = feignTimeOutSingle.getTimeOutSingle();
        logger.error("11111");
        logger.debug("222222");
        logger.info("2121");
        logger.info("44444");
        System.out.println(timeOut);
    }
}
