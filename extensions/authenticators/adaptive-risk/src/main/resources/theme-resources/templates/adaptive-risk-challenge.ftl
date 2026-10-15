<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "header">
        ${msg("adaptiveRiskChallengeTitle")}
    <#elseif section = "form">
        <div id="kc-adaptive-risk" class="adaptive-risk-container" style="text-align: center; font-family: inherit;">
            <div style="background-color: #fffaf0; border: 1px solid #feebc8; border-radius: 8px; padding: 20px; margin-bottom: 24px; text-align: left;">
                <div style="display: flex; align-items: center; margin-bottom: 12px;">
                    <span style="font-size: 24px; margin-right: 12px; color: #dd6b20;">&#9888;</span>
                    <h3 style="margin: 0; font-size: 16px; color: #7b341e; font-weight: 600;">
                        ${msg("adaptiveRiskNoticeHeading")}
                    </h3>
                </div>
                <p style="font-size: 14px; color: #744210; margin-bottom: 16px; line-height: 1.5;">
                    ${msg("adaptiveRiskNoticeMessage")}
                </p>

                <div style="background: white; border: 1px solid #fbd38d; border-radius: 6px; padding: 12px; font-size: 13px;">
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 4px 0; color: #718096; width: 40%;">${msg("adaptiveRiskFieldLocation")}:</td>
                            <td style="padding: 4px 0; color: #2d3748; font-weight: 500;">${locationDesc!"Unknown"}</td>
                        </tr>
                        <tr>
                            <td style="padding: 4px 0; color: #718096;">${msg("adaptiveRiskFieldIp")}:</td>
                            <td style="padding: 4px 0; color: #2d3748; font-weight: 500;">${clientIp!"Unknown"}</td>
                        </tr>
                        <tr>
                            <td style="padding: 4px 0; color: #718096;">${msg("adaptiveRiskFieldRiskLevel")}:</td>
                            <td style="padding: 4px 0; color: #c05621; font-weight: bold;">${riskLevel!"MEDIUM"} (${riskScore!"50"}/100)</td>
                        </tr>
                    </table>
                </div>
            </div>

            <form id="kc-adaptive-risk-form" class="form-horizontal" action="${url.loginAction}" method="post">
                <div class="form-group" style="margin-top: 20px;">
                    <button type="submit" name="action" value="continue" class="btn btn-primary btn-block btn-lg" style="width: 100%; padding: 10px; background: #3182ce; color: white; border: none; border-radius: 6px; font-size: 15px; font-weight: bold; cursor: pointer;">
                        ${msg("adaptiveRiskContinueBtn")}
                    </button>
                </div>
                <div class="form-group" style="margin-top: 10px;">
                    <button type="submit" name="action" value="cancel" class="btn btn-default btn-block" style="width: 100%; padding: 8px; background: transparent; color: #718096; border: 1px solid #cbd5e0; border-radius: 6px; font-size: 14px; cursor: pointer;">
                        ${msg("adaptiveRiskCancelBtn")}
                    </button>
                </div>
            </form>
        </div>
    </#if>
</@layout.registrationLayout>
