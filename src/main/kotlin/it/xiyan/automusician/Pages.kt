package it.xiyan.automusician

import io.ktor.html.*
import kotlinx.html.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {  }

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
                    width: 40%;
                    height: 30px;
                    border: 1px solid black;
                    border-radius: 5%;
                    margin-left: 29.5%;
                    margin-right: 29.5%;
                }
                
                .hidden {
                    display: none;
                }
            """
        }
    }

    body(classes = "center") {
        +"扫描下方二维码，登录网易云"
        br ()
        img (src = NeteaseCloud.getLoginQrCode(loginUUID).loginQrCodeBlob, alt = "Login Qr Code")
        div(classes = "center loginResult") {

        }
        button(classes = "center") {
            attributes["onclick"] = "location.reload();"
            +"刷新二维码"
        }

        script {
            unsafe {
                +"""
                    let preload = document.getElementById("$preloadLogin");
                    let loginInfo = JSON.parse(preload.content);
                    
                    let loginResultWs = new WebSocket((location.protocol === "https" ? "wss://" : "ws://") + 
                        location.host + "/api/login/check?id=" + loginInfo["loginId"]);
                    let resultShower = document.getElementsByClassName("loginResult")[0];
                    loginResultWs.onmessage = function (event) {
                        console.debug ("收到回复", event.data)
                        resultShower.innerHTML = JSON.parse(event.data).message
                    }
                    loginResultWs.onclose = function () {console.warn ("回报连接已关闭.")}
                    loginResultWs.onerror = function () { console.error ("发生错误", arguments)}
                """
            }
        }
    }
}

class NeteaseCloudLogin: Template<HTML> {
    val qrCodeImg = Placeholder<FlowContent>()
    override fun HTML.apply() {
        head {

        }

        body {
            insert(qrCodeImg)
        }
    }
}





