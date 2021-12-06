@file:Suppress("DEPRECATION")

package net.lamgc.automusician

import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.routing.*
import kotlinx.html.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

fun Routing.pages() {
    get("/") {
        call.respondHtml(HttpStatusCode.OK, HTML::index)
    }

    get("/qrlogin") {
        call.respondHtml(HttpStatusCode.OK, HTML::loginPage)
    }
}

fun HTML.index() {
    head {
        title("网易云工人")
        style {
            +"""
                .center {
                    text-align: center;
                }
            """.trimIndent()
        }
    }
    body {
        div(classes = "center") {
            +"网易云音乐工人"
            br()
            +"一个很棒的自动工人程序！"
        }
        div(classes = "center") {
            a(href = "qrlogin") {
                button {
                    +"二维码登录"
                }
            }
        }
    }
}

@Suppress("CssUnusedSymbol", "JSUnresolvedVariable")
fun HTML.loginPage() {
    val loginUUID = NeteaseCloud.createLoginQrCodeId()
    val preloadLogin = "preload-login"
    logger.debug { "新的登录请求, NC Login Id: $loginUUID, Web Login Id: ${HashcodeSet.getHash(loginUUID)}" }
    head {
        title("登录网易云音乐人 - 自动音乐人")
        meta(name = "loginInfo", content = QrCodeLoginMonitor.createWebLoginSession(loginUUID)) {
            id = preloadLogin
        }
        style {
            //language=CSS
            +"""
                .center {
                    text-align: center;
                }
                
                .loginResult {
                    font-size: 18px;
                    width: 40%;
                    height: 70px;
                    border: 1px solid black;
                    margin-left: 29.5%;
                    margin-right: 29.5%;
                }
                
                .hidden {
                    display: none;
                }
            """.trimIndent()
        }
    }

    body(classes = "center") {
        +"扫描下方二维码，登录网易云"
        br()
        img(src = NeteaseCloud.getLoginQrCode(loginUUID).loginQrCodeBlob, alt = "Login Qr Code")
        textArea(classes = "center loginResult") {
            attributes["readonly"] = "readonly"
        }
        button(classes = "center") {
            attributes["onclick"] = "location.reload();"
            +"刷新二维码"
        }

        script {
            unsafe {
                // language=JavaScript
                +"""
                    let preload = document.getElementById("$preloadLogin");
                    let loginInfo = JSON.parse(preload.content);
                    
                    let loginResultWs = new WebSocket((location.protocol === "https:" ? "wss://" : "ws://") + 
                        location.host + "/api/login/check?id=" + loginInfo["loginId"]);
                    let resultShower = document.getElementsByClassName("loginResult")[0];
                    loginResultWs.onmessage = function (event) {
                        console.debug ("收到回复", event.data);
                        let response = JSON.parse(event.data);
                        if (response["confirm"] === true) {
                            resultShower.innerHTML = "等待扫码";
                            return;
                        }
                        if (response["success"] === true) {
                            resultShower.innerHTML = "登录成功！网易云用户：" + response["userName"] +
                                "(" + response["userId"] + ")，" + (response["repeatLogin"] === true ? 
                                "该帐号已成功更新登录信息(如旧的登录凭证仍然有效, 将自动登出以销毁凭证), 上一次登录时间为 " + 
                                new Date(response["lastLogin"]) :
                            "该帐号在本站为首次登录.")
                        } else {
                            resultShower.innerHTML = response["message"];
                        }
                    }
                    loginResultWs.onclose = function () {console.warn ("回报连接已关闭.")}
                    loginResultWs.onerror = function () { console.error ("发生错误", arguments)}
                """.trimIndent()
            }
        }
    }
}
