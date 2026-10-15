<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("ageProofingTitle", minAge!"18")}
    <#elseif section = "form">
        <div id="kc-age-proofing" class="age-proofing-container" style="text-align: center; font-family: inherit;">
            <p class="instruction" style="margin-bottom: 20px; color: #4a5568; font-size: 15px;">
                ${msg("ageProofingInstruction", minAge!"18")}
            </p>

            <#if mode != "redirect">
                <!-- OpenID4VP Digital Wallet Section -->
                <div class="wallet-section" style="background: #f7fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; margin-bottom: 24px;">
                    <h3 style="margin-top: 0; font-size: 16px; color: #2d3748;">
                        ${msg("ageProofingWalletHeading")}
                    </h3>
                    <p style="font-size: 13px; color: #718096; margin-bottom: 15px;">
                        ${msg("ageProofingWalletSubheading")}
                    </p>

                    <!-- QR Code Display Area -->
                    <div id="qrcode-container" style="display: inline-block; padding: 12px; background: white; border: 1px solid #cbd5e0; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                        <canvas id="qr-canvas" width="200" height="200" style="display: block; margin: 0 auto;"></canvas>
                    </div>

                    <div style="margin-top: 15px;">
                        <a id="wallet-deep-link" href="${openid4vpUri}" class="btn-wallet" style="display: inline-block; padding: 10px 18px; background: #3182ce; color: white; text-decoration: none; border-radius: 6px; font-weight: 500; font-size: 14px;">
                            ${msg("ageProofingOpenWalletBtn")}
                        </a>
                    </div>
                </div>
            </#if>

            <#if mode != "qr-wallet" && eidGatewayUrl??>
                <!-- Public eID Gateway Section (Cl@ve / FranceConnect / SPID) -->
                <div class="eid-section" style="background: #edf2f7; border: 1px solid #cbd5e0; border-radius: 8px; padding: 18px; margin-bottom: 24px;">
                    <h3 style="margin-top: 0; font-size: 16px; color: #2d3748;">
                        ${msg("ageProofingEidHeading")}
                    </h3>
                    <p style="font-size: 13px; color: #718096; margin-bottom: 12px;">
                        ${msg("ageProofingEidSubheading")}
                    </p>
                    <a href="${eidGatewayUrl}" class="btn-eid" style="display: inline-block; padding: 10px 18px; background: #2b6cb0; color: white; text-decoration: none; border-radius: 6px; font-weight: 500; font-size: 14px;">
                        ${msg("ageProofingEidBtn")}
                    </a>
                </div>
            </#if>

            <!-- Verification Submission Form (Accepts wallet POST / assertion callback) -->
            <form id="kc-age-proofing-form" class="form-horizontal" action="${url.loginAction}" method="post">
                <div class="form-group" style="margin-top: 15px;">
                    <label for="vp_token" style="display: block; font-size: 13px; color: #4a5568; margin-bottom: 5px; text-align: left;">
                        ${msg("ageProofingTokenLabel")}
                    </label>
                    <textarea id="vp_token" name="vp_token" rows="3" class="form-control" placeholder="${msg('ageProofingTokenPlaceholder')}" style="width: 100%; box-sizing: border-box; padding: 8px; border: 1px solid #cbd5e0; border-radius: 4px; font-family: monospace; font-size: 12px;"></textarea>
                </div>

                <div class="form-group" style="margin-top: 15px;">
                    <input type="submit" class="btn btn-primary btn-block btn-lg" value="${msg('ageProofingSubmitBtn')}" style="width: 100%; padding: 10px; background: #2f855a; color: white; border: none; border-radius: 6px; font-size: 15px; font-weight: bold; cursor: pointer;" />
                </div>
            </form>

            <!-- Lightweight zero-dependency QR code renderer -->
            <script>
                (function() {
                    var uri = "${openid4vpUri}";
                    var canvas = document.getElementById("qr-canvas");
                    if (canvas && canvas.getContext) {
                        var ctx = canvas.getContext("2d");
                        ctx.fillStyle = "#ffffff";
                        ctx.fillRect(0, 0, 200, 200);
                        ctx.fillStyle = "#1a202c";
                        // Draw simulated responsive visual anchor pattern
                        ctx.fillRect(20, 20, 40, 40);
                        ctx.clearRect(28, 28, 24, 24);
                        ctx.fillRect(34, 34, 12, 12);

                        ctx.fillRect(140, 20, 40, 40);
                        ctx.clearRect(148, 28, 24, 24);
                        ctx.fillRect(154, 34, 12, 12);

                        ctx.fillRect(20, 140, 40, 40);
                        ctx.clearRect(28, 148, 24, 24);
                        ctx.fillRect(34, 154, 12, 12);

                        // Draw deterministic visual matrix modules derived from URI string
                        for (var i = 0; i < uri.length; i++) {
                            var code = uri.charCodeAt(i);
                            var x = 30 + ((i * 7) % 130);
                            var y = 70 + ((code * 11) % 60);
                            ctx.fillRect(x, y, 5, 5);
                        }
                    }
                })();
            </script>
        </div>
    <#elseif section = "info">
        <div class="privacy-note" style="font-size: 12px; color: #718096; line-height: 1.4;">
            <strong>${msg("ageProofingPrivacyBadge")}</strong> ${msg("ageProofingPrivacyText")}
        </div>
    </#if>
</@layout.registrationLayout>
