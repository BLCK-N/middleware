package com.myrtle.middleware.services;

import com.myrtle.middleware.dtos.request.EditUserRequest;
import com.myrtle.middleware.dtos.request.NextOfKinRequest;
import com.myrtle.middleware.dtos.request.Tier3UpgradePayLoad;
import com.myrtle.middleware.enums.TransactionAction;
import com.myrtle.middleware.model.*;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final ExternalAPIs externalAPIs;
    @Value("${app.host}")
    private String host;
    @Value("${frontend.url}")
    private String frontendUrl;
    @Value("${app.support.email:wecare@myrtleng.com}")
    private String supportEmail;
    @Value("${app.support.phone:}")
    private String supportPhone;
    @Value("${app.onboarding.internal-notify-email:wecare@myrtleng.com}")
    private String onboardingInternalNotifyEmail;

    public void deliverOtp(String to, String otp) {
        String subject = "One Time Password";
        String logoUrl = "https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png";
        String safeOtp = otp == null ? "" : otp.trim();
        String otpDigits = safeOtp.chars()
                .mapToObj(ch -> "<div class='otp-digit highlight'>" + escapeHtml(String.valueOf((char) ch)) + "</div>")
                .collect(Collectors.joining());

        String html = """
                <!doctype html>
                <html lang='en'>
                  <head>
                    <meta charset='UTF-8' />
                    <meta name='viewport' content='width=device-width, initial-scale=1.0' />
                    <title>Myrtle – Verify Your Identity</title>
                    <link rel='preconnect' href='https://fonts.googleapis.com' />
                    <link rel='preconnect' href='https://fonts.gstatic.com' crossorigin />
                    <link
                      href='https://fonts.googleapis.com/css2?family=Mulish:ital,wght@0,200..1000;1,200..1000&display=swap'
                      rel='stylesheet'
                    />
                    <style>
                      * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                      }
                
                      body {
                        background-color: #f2f5f2;
                        font-family: 'Mulish', sans-serif;
                        -webkit-font-smoothing: antialiased;
                        padding: 40px 16px;
                      }
                
                      .logo-name {
                        color: #ffffff;
                      }
                
                      .wrapper {
                        max-width: 560px;
                        margin: 0 auto;
                      }
                
                      .header {
                        background-color: #42995a;
                        border-radius: 16px 16px 0 0;
                        padding: 28px 40px;
                        display: flex;
                        align-items: center;
                        gap: 12px;
                      }
                
                      .brand-name {
                        font-family: 'Mulish', sans-serif;
                        font-size: 22px;
                        color: white;
                        letter-spacing: 0.3px;
                      }
                
                      .card {
                        background: #ffffff;
                        padding: 48px 40px 40px;
                        border-left: 1px solid #e8ede8;
                        border-right: 1px solid #e8ede8;
                      }
                
                      .eyebrow {
                        font-size: 11px;
                        font-weight: 600;
                        letter-spacing: 2px;
                        text-transform: uppercase;
                        color: #42995a;
                        margin-bottom: 12px;
                      }
                
                      .heading {
                        font-family: 'Mulish', sans-serif;
                        font-size: 28px;
                        color: #1a2e1a;
                        line-height: 1.25;
                        margin-bottom: 16px;
                      }
                
                      .heading em {
                        font-style: normal;
                        color: #42995a;
                      }
                
                      .body-text {
                        font-size: 15px;
                        color: #5a6b5a;
                        line-height: 1.7;
                        margin-bottom: 36px;
                      }
                
                      .otp-container {
                        background: #f7fbf7;
                        border: 1.5px solid #c8e6cc;
                        border-radius: 14px;
                        padding: 32px 24px;
                        text-align: center;
                        margin-bottom: 32px;
                        position: relative;
                        overflow: hidden;
                      }
                
                      .otp-container::before {
                        content: '';
                        position: absolute;
                        top: -30px;
                        right: -30px;
                        width: 100px;
                        height: 100px;
                        background: radial-gradient(
                          circle,
                          rgba(66, 153, 90, 0.08) 0%,
                          transparent 70%
                        );
                        border-radius: 50%;
                      }
                
                      .otp-label {
                        font-size: 11px;
                        font-weight: 600;
                        letter-spacing: 2px;
                        text-transform: uppercase;
                        color: #42995a;
                        margin-bottom: 16px;
                        opacity: 0.8;
                      }
                
                      .otp-digits {
                        display: flex;
                        justify-content: center;
                        gap: 10px;
                        margin-bottom: 20px;
                        flex-wrap: wrap;
                      }
                
                      .otp-digit {
                        width: 52px;
                        height: 60px;
                        background: white;
                        border: 2px solid #c8e6cc;
                        border-radius: 10px;
                        display: inline-block;
                        text-align: center;
                        line-height: 56px;
                        vertical-align: middle;
                        padding: 0;
                        font-family: 'Mulish', sans-serif;
                        font-size: 28px;
                        font-weight: 700;
                        color: #1a2e1a;
                        box-shadow: 0 2px 8px rgba(66, 153, 90, 0.08);
                      }
                
                      .otp-digit.highlight {
                        border-color: #42995a;
                        background: #f0faf2;
                      }
                
                      .expiry-badge {
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                        background: #e7fff1;
                        border: 1px solid #42995a;
                        border-radius: 20px;
                        padding: 6px 14px;
                        font-size: 12px;
                        font-weight: 500;
                        color: #42995a;
                      }
                
                      .expiry-badge svg {
                        width: 13px;
                        height: 13px;
                        fill: #42995a;
                        flex-shrink: 0;
                      }
                
                      .divider {
                        height: 1px;
                        background: linear-gradient(
                          to right,
                          transparent,
                          #e0eae0,
                          transparent
                        );
                        margin: 32px 0;
                      }
                
                      .security-note {
                        background: #fafffe;
                        border-left: 3px solid #42995a;
                        border-radius: 0 8px 8px 0;
                        padding: 14px 18px;
                        margin-bottom: 28px;
                      }
                
                      .security-note p {
                        font-size: 13px;
                        color: #4a5e4a;
                        line-height: 1.6;
                      }
                
                      .security-note strong {
                        color: #1a2e1a;
                      }
                
                      .sign-off {
                        font-size: 14px;
                        color: #7a8e7a;
                        line-height: 1.7;
                      }
                
                      .sign-off strong {
                        color: #1a2e1a;
                        display: block;
                        margin-top: 12px;
                        font-size: 15px;
                      }
                
                      .footer {
                        background: #42995a;
                        border-radius: 0 0 16px 16px;
                        padding: 24px 40px;
                        text-align: center;
                        border-top: 3px solid #367d4a;
                      }
                
                      .footer p {
                        font-size: 12px;
                        color: rgba(255, 255, 255, 0.65);
                        line-height: 1.7;
                      }
                
                      .footer a {
                        color: rgba(255, 255, 255, 0.85);
                        text-decoration: underline;
                      }
                
                      .footer .footer-brand {
                        font-family: 'Mulish', sans-serif;
                        font-size: 14px;
                        color: white;
                        margin-bottom: 8px;
                      }
                
                      @media (max-width: 480px) {
                        .card {
                          padding: 36px 24px 32px;
                        }
                        .header {
                          padding: 22px 24px;
                        }
                        .footer {
                          padding: 20px 24px;
                        }
                        .otp-digit {
                          width: 42px;
                          height: 52px;
                          line-height: 48px;
                          font-size: 22px;
                        }
                        .otp-digits {
                          gap: 7px;
                        }
                        .heading {
                          font-size: 24px;
                        }
                      }
                    </style>
                  </head>
                  <body>
                    <div class='wrapper'>
                      <div class='header'>
                        <span class='brand-name'>
                          <img
                            src='__LOGO_URL__'
                            alt='Myrtle Logo'
                            width='120'
                            height='28'
                          />
                        </span>
                      </div>
                
                      <div class='card'>
                        <p class='eyebrow'>Security Verification</p>
                        <h1 class='heading'>Your one-time<br /><em>verification code</em></h1>
                
                        <p class='body-text'>
                          We received a request to verify your identity on your Myrtle account.
                          Use the code below to complete the process. Do not share this code
                          with anyone.
                        </p>
                
                        <div class='otp-container'>
                          <p class='otp-label'>Your OTP Code</p>
                          <div class='otp-digits'>
                            __OTP_DIGITS__
                          </div>
                          <div class='expiry-badge'>
                            <svg viewBox='0 0 24 24' xmlns='http://www.w3.org/2000/svg'>
                              <path
                                d='M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z'
                              />
                            </svg>
                            Expires in 5 minutes
                          </div>
                        </div>
                
                        <div class='divider'></div>
                
                        <div class='security-note'>
                          <p>
                            <strong>Didn't request this?</strong> If you didn't initiate this
                            verification, please ignore this email. Your account remains secure.
                            Never share your OTP with anyone
                            <strong>Myrtle asset staff will never ask for it</strong>.
                          </p>
                        </div>
                
                        <p class='sign-off'>
                          Thank you for keeping your account secure with us.
                          <strong>Myrtle Asset Management & Trust Limited</strong>
                        </p>
                      </div>
                
                      <div class='footer'>
                        <p class='footer-brand'>Myrtle</p>
                        <p>
                          You're receiving this email because a verification was requested for
                          your account.<br />
                          If you have questions, contact us at
                          <a href='mailto:wecare@myrtleng.com'>wecare@myrtleng.com</a>
                        </p>
                        <p style='margin-top: 10px'>
                          &copy; __YEAR__ Myrtle Financial Group. All rights reserved.
                        </p>
                      </div>
                    </div>
                  </body>
                </html>
                """;

        String body = html
                .replace("__LOGO_URL__", logoUrl)
                .replace("__OTP_DIGITS__", otpDigits)
                .replace("__YEAR__", String.valueOf(Year.now().getValue()));

        log.info("Sending OTP email | to={}", to);
        externalAPIs.sendTransactionalEmail(to, subject, body);
    }

    public void deliverFamilyOfficeReport(String to, String aiHtml) {
        String subject = "Your Family Wealth Clarity Report™ – Myrtle Family Office";
        String logoUrl = "https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png";

        String reportContent = extractBodyContent(aiHtml);

        String html = """
                <!doctype html>
                <html lang='en'>
                  <head>
                    <meta charset='UTF-8' />
                    <meta name='viewport' content='width=device-width, initial-scale=1.0' />
                    <title>Myrtle – Family Wealth Clarity Report™</title>
                    <link rel='preconnect' href='https://fonts.googleapis.com' />
                    <link rel='preconnect' href='https://fonts.gstatic.com' crossorigin />
                    <link
                      href='https://fonts.googleapis.com/css2?family=Mulish:ital,wght@0,200..1000;1,200..1000&display=swap'
                      rel='stylesheet'
                    />
                    <style>
                      * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                      }
                
                      body {
                        background-color: #f2f5f2;
                        font-family: 'Mulish', sans-serif;
                        -webkit-font-smoothing: antialiased;
                        padding: 40px 16px;
                      }
                
                      .wrapper {
                        max-width: 800px;
                        margin: 0 auto;
                      }
                
                      .header {
                        background-color: #42995a;
                        border-radius: 16px 16px 0 0;
                        padding: 28px 40px;
                        display: flex;
                        align-items: center;
                        gap: 12px;
                      }
                
                      .brand-name {
                        font-family: 'Mulish', sans-serif;
                        font-size: 22px;
                        color: white;
                        letter-spacing: 0.3px;
                      }
                
                      .card {
                        background: #ffffff;
                        padding: 0;
                        border-left: 1px solid #e8ede8;
                        border-right: 1px solid #e8ede8;
                      }
                
                      .footer {
                        background: #42995a;
                        border-radius: 0 0 16px 16px;
                        padding: 24px 40px;
                        text-align: center;
                        border-top: 3px solid #367d4a;
                      }
                
                      .footer p {
                        font-size: 12px;
                        color: rgba(255, 255, 255, 0.65);
                        line-height: 1.7;
                      }
                
                      .footer a {
                        color: rgba(255, 255, 255, 0.85);
                        text-decoration: underline;
                      }
                
                      .footer .footer-brand {
                        font-family: 'Mulish', sans-serif;
                        font-size: 14px;
                        color: white;
                        margin-bottom: 8px;
                      }
                
                      @media (max-width: 480px) {
                        .header {
                          padding: 22px 24px;
                        }
                        .footer {
                          padding: 20px 24px;
                        }
                      }
                    </style>
                  </head>
                  <body>
                    <div class='wrapper'>
                      <div class='header'>
                        <span class='brand-name'>
                          <img
                            src='__LOGO_URL__'
                            alt='Myrtle Logo'
                            width='120'
                            height='28'
                          />
                        </span>
                      </div>
                
                      <div class='card'>
                        __REPORT_CONTENT__
                      </div>
                
                      <div class='footer'>
                        <p class='footer-brand'>Myrtle</p>
                        <p>
                          This report was prepared by Myrtle Family Office and is strictly confidential.<br />
                          If you have questions, contact us at
                          <a href='mailto:wecare@myrtleng.com'>wecare@myrtleng.com</a>
                        </p>
                        <p style='margin-top: 10px'>
                          &copy; __YEAR__ Myrtle Financial Group. All rights reserved.
                        </p>
                      </div>
                    </div>
                  </body>
                </html>
                """;

        String body = html
                .replace("__LOGO_URL__", logoUrl)
                .replace("__REPORT_CONTENT__", reportContent)
                .replace("__YEAR__", String.valueOf(Year.now().getValue()));

        log.info("Sending family office report email | to={}", to);
        externalAPIs.sendTransactionalEmail(to, subject, body);
    }

    public void deliverQuestionnaireReport(String to, String reportHtml) {
        String subject = "Your Client Discovery & Suitability Summary – Myrtle";
        String logoUrl = "https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png";

        String reportContent = extractBodyContent(reportHtml);

        String html = """
                <!doctype html>
                <html lang='en'>
                  <head>
                    <meta charset='UTF-8' />
                    <meta name='viewport' content='width=device-width, initial-scale=1.0' />
                    <title>Myrtle – Client Discovery &amp; Suitability Summary</title>
                    <link rel='preconnect' href='https://fonts.googleapis.com' />
                    <link rel='preconnect' href='https://fonts.gstatic.com' crossorigin />
                    <link
                      href='https://fonts.googleapis.com/css2?family=Mulish:ital,wght@0,200..1000;1,200..1000&display=swap'
                      rel='stylesheet'
                    />
                    <style>
                      * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                      }
                
                      body {
                        background-color: #f2f5f2;
                        font-family: 'Mulish', sans-serif;
                        -webkit-font-smoothing: antialiased;
                        padding: 40px 16px;
                      }
                
                      .wrapper {
                        max-width: 800px;
                        margin: 0 auto;
                      }
                
                      .header {
                        background-color: #42995a;
                        border-radius: 16px 16px 0 0;
                        padding: 28px 40px;
                        display: flex;
                        align-items: center;
                        gap: 12px;
                      }
                
                      .brand-name {
                        font-family: 'Mulish', sans-serif;
                        font-size: 22px;
                        color: white;
                        letter-spacing: 0.3px;
                      }
                
                      .card {
                        background: #ffffff;
                        padding: 0;
                        border-left: 1px solid #e8ede8;
                        border-right: 1px solid #e8ede8;
                      }
                
                      .footer {
                        background: #42995a;
                        border-radius: 0 0 16px 16px;
                        padding: 24px 40px;
                        text-align: center;
                        border-top: 3px solid #367d4a;
                      }
                
                      .footer p {
                        font-size: 12px;
                        color: rgba(255, 255, 255, 0.65);
                        line-height: 1.7;
                      }
                
                      .footer a {
                        color: rgba(255, 255, 255, 0.85);
                        text-decoration: underline;
                      }
                
                      .footer .footer-brand {
                        font-family: 'Mulish', sans-serif;
                        font-size: 14px;
                        color: white;
                        margin-bottom: 8px;
                      }
                
                      @media (max-width: 480px) {
                        .header {
                          padding: 22px 24px;
                        }
                        .footer {
                          padding: 20px 24px;
                        }
                      }
                    </style>
                  </head>
                  <body>
                    <div class='wrapper'>
                      <div class='header'>
                        <span class='brand-name'>
                          <img
                            src='__LOGO_URL__'
                            alt='Myrtle Logo'
                            width='120'
                            height='28'
                          />
                        </span>
                      </div>
                
                      <div class='card'>
                        __REPORT_CONTENT__
                      </div>
                
                      <div class='footer'>
                        <p class='footer-brand'>Myrtle</p>
                        <p>
                          This summary was prepared by Myrtle and is strictly confidential.<br />
                          If you have questions, contact us at
                          <a href='mailto:wecare@myrtleng.com'>wecare@myrtleng.com</a>
                        </p>
                        <p style='margin-top: 10px'>
                          &copy; __YEAR__ Myrtle Financial Group. All rights reserved.
                        </p>
                      </div>
                    </div>
                  </body>
                </html>
                """;

        String body = html
                .replace("__LOGO_URL__", logoUrl)
                .replace("__REPORT_CONTENT__", reportContent)
                .replace("__YEAR__", String.valueOf(Year.now().getValue()));

        log.info("Sending questionnaire report email | to={}", to);
        externalAPIs.sendTransactionalEmail(to, subject, body);
    }

    private String extractBodyContent(String fullHtml) {
        if (fullHtml == null) return "";
        String lower = fullHtml.toLowerCase();
        int bodyOpen = lower.indexOf("<body");
        if (bodyOpen == -1) return fullHtml;
        int contentStart = fullHtml.indexOf('>', bodyOpen) + 1;
        int bodyClose = lower.lastIndexOf("</body>");
        if (bodyClose <= contentStart) return fullHtml;
        return fullHtml.substring(contentStart, bodyClose).trim();
    }

    public void sendWelcomeEmail(User user) {
        String subject = "Myrtle – Welcome";
        String logoUrl = "https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png";
        String firstName = user.getFirstName() == null ? "there" : escapeHtml(user.getFirstName());

        String html = """
                <!doctype html>
                <html lang='en'>
                  <head>
                    <meta charset='UTF-8' />
                    <meta name='viewport' content='width=device-width, initial-scale=1.0' />
                    <title>Myrtle – Welcome</title>
                    <link
                      href='https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=DM+Sans:wght@300;400;500;600&display=swap'
                      rel='stylesheet'
                    />
                  </head>
                  <body
                    style='
                      background-color: #f2f5f2;
                      font-family: "DM Sans", sans-serif;
                      padding: 40px 16px;
                    '
                  >
                    <div style='max-width: 560px; margin: 0 auto'>
                      <div
                        style='
                          background-color: #42995a;
                          border-radius: 16px 16px 0 0;
                          padding: 28px 40px;
                          display: flex;
                          align-items: center;
                          gap: 12px;
                        '
                      >
                        <div></div>
                        <div>
                          <img src='__LOGO_URL__' alt='Myrtle Logo' width='120' height='32' />
                        </div>
                      </div>
                
                      <div
                        style='
                          background: #ffffff;
                          padding: 48px 40px 40px;
                          border-left: 1px solid #e8ede8;
                          border-right: 1px solid #e8ede8;
                        '
                      >
                        <p
                          style='
                            font-size: 11px;
                            font-weight: 600;
                            letter-spacing: 2px;
                            text-transform: uppercase;
                            color: #42995a;
                            margin-bottom: 12px;
                          '
                        >
                          Welcome to Myrtle
                        </p>
                
                        <h1
                          style='
                            font-family: "DM Serif Display", serif;
                            font-size: 28px;
                            color: #1a2e1a;
                            line-height: 1.25;
                            margin-bottom: 16px;
                          '
                        >
                          Your journey to<br /><em style='color: #42995a'>smarter finance</em>
                          starts here
                        </h1>
                
                        <p
                          style='
                            font-size: 15px;
                            color: #5a6b5a;
                            line-height: 1.7;
                            margin-bottom: 32px;
                          '
                        >
                          Hello __FIRST_NAME__,<br /><br />
                          My name is Oluwaseun Adesoye, the Group Executive of Myrtle financial
                          group comprising Myrtle Asset Management and Trust Limited, Octopus
                          Microfinance and Myrtle Professional services. <br />
                          I and my team are Happy goal-centric professionals, who CARE, value
                          Innovation, take Responsibility and are obsessed with Excellence. We
                          understand that your path to wealth before now and going forward
                          impacts every area of your life, thus we are deliberate about spotting
                          opportunities which can jointly aid us in arriving at that preferred
                          destination safely. <br />
                          <br />
                          You can login to our client portal with the details you set during
                          registration.
                        </p>
                
                        <div
                          style='
                            background: #f7fbf7;
                            border: 1.5px solid #c8e6cc;
                            border-radius: 14px;
                            padding: 28px 24px;
                            text-align: left;
                            margin-bottom: 32px;
                          '
                        >
                          <p
                            style='
                              font-size: 13px;
                              font-weight: 600;
                              letter-spacing: 1px;
                              color: #42995a;
                              margin-bottom: 14px;
                            '
                          >
                            WHAT YOU CAN DO NEXT
                          </p>
                
                          <ul
                            style='
                              padding-left: 18px;
                              color: #1a2e1a;
                              font-size: 14px;
                              line-height: 1.8;
                            '
                          >
                            <li>Secure your account with full verification</li>
                            <li>Explore your personalized dashboard</li>
                            <li>Start managing and tracking your finances</li>
                          </ul>
                        </div>
                
                        <div
                          style='
                            height: 1px;
                            background: linear-gradient(
                              to right,
                              transparent,
                              #e0eae0,
                              transparent
                            );
                            margin: 32px 0;
                          '
                        ></div>
                
                        <div
                          style='
                            background: #fafffe;
                            border-left: 3px solid #42995a;
                            border-radius: 0 8px 8px 0;
                            padding: 14px 18px;
                            margin-bottom: 28px;
                          '
                        >
                          <p style='font-size: 13px; color: #4a5e4a; line-height: 1.6'>
                            <strong style='color: #1a2e1a'>Security tip:</strong> Always keep
                            your login details safe. Myrtle will never ask for your password or
                            private codes via email.
                          </p>
                        </div>
                
                        <p style='font-size: 14px; color: #7a8e7a; line-height: 1.7'>
                          We’re excited to have you with us and can’t wait to support your
                          financial growth.
                          <strong
                            style='
                              color: #1a2e1a;
                              display: block;
                              margin-top: 12px;
                              font-size: 15px;
                            '
                          >
                            The Myrtle Team 🌿
                          </strong>
                        </p>
                      </div>
                
                      <div
                        style='
                          background: #42995a;
                          border-radius: 0 0 16px 16px;
                          padding: 24px 40px;
                          text-align: center;
                          border-top: 3px solid #367d4a;
                        '
                      >
                        <p
                          style='
                            font-size: 12px;
                            color: rgba(255, 255, 255, 0.65);
                            line-height: 1.7;
                          '
                        >
                          <span
                            style='
                              font-family: "DM Serif Display", serif;
                              font-size: 14px;
                              color: white;
                            '
                            >Myrtle Asset Management & Trust Limited</span
                          ><br />
                          You're receiving this email because you created an account with
                          Myrtle.<br />
                          If you have questions, contact us at
                          <a
                            href='mailto:wecare@myrtleng.com'
                            style='color: rgba(255, 255, 255, 0.85); text-decoration: underline'
                          >
                            wecare@myrtleng.com
                          </a>
                        </p>
                
                        <p
                          style='
                            margin-top: 10px;
                            font-size: 12px;
                            color: rgba(255, 255, 255, 0.65);
                          '
                        >
                          &copy; __YEAR__ Myrtle. All rights reserved.
                        </p>
                      </div>
                    </div>
                  </body>
                </html>
                """;

        String body = html
                .replace("__LOGO_URL__", logoUrl)
                .replace("__FIRST_NAME__", firstName)
                .replace("__YEAR__", String.valueOf(Year.now().getValue()));

        log.info("Sending welcome email | to={}", user.getEmailAddress());
        externalAPIs.sendTransactionalEmail(user.getEmailAddress(), subject, body);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String esc(String value) {
        if (value == null) {
            return "—";
        }
        return escapeHtml(value);
    }

    public void sendSurveyResponseMail(
            String to,
            User user,
            String persona,
            String result
    ) {

        String subject = "New Survey Response from " + user.getFirstName() + " " + user.getLastName();

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8' />")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0' />")
                .append("<title>Email Template</title>")
                .append("<link rel='preconnect' href='https://fonts.googleapis.com' />")
                .append("<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin />")
                .append("<link href='https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;500;600;700&display=swap' rel='stylesheet' />")
                .append("</head>")
                .append("<body style='margin:0;padding:20px 0;background:white;font-family: \"IBM Plex Sans\", Arial, sans-serif;'>")

                .append("<table width='100%' cellpadding='0' cellspacing='0' bgcolor='#ffffff' style='padding:20px'>")

                // TOP LOGOS
                .append("<tr>")
                .append("<td align='center' style='padding:20px 0;'>")
                .append("<table cellpadding='0' cellspacing='0' width='100%'>")
                .append("<tr>")

                .append("<td align='left' style='padding:0 10px;'>")
                .append("<img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557866/Cash_sopn3k.png' alt='Cash' style='display:block;'>")
                .append("</td>")

                .append("<td align='right' style='padding:0 10px;'>")
                .append("<img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png' alt='Myrtle' style='display:block;'>")
                .append("</td>")

                .append("<td align='right' style='padding:0 10px;'></td>")

                .append("</tr>")
                .append("</table>")
                .append("</td>")
                .append("</tr>")

                // MAIN CONTENT
                .append("<tr>")
                .append("<td align='left'>")
                .append("<table width='600' cellpadding='0' cellspacing='0' style='margin:0 auto;font-family:\"IBM Plex Sans\", Arial, sans-serif;'>")

                .append("<tr>")
                .append("<td style='padding:0 0 20px 0'>")
                .append("<h1 style='font-weight:600;margin:0;'>Myrtle Money Audit <br/> Report</h1>")
                .append("</td>")
                .append("</tr>")

                .append("<tr>")
                .append("<td style='padding-bottom:20px'>")
                .append("<p style='font-weight:300;line-height:28px;margin:0;'>")
                .append("<b style='font-weight:700'>Hello ").append(user.getFirstName()).append(",</b> <br/>")
                .append("Thank you for completing the Myrtle Money Audit! Based on your responses. Here’s your Myrtle Money Clarity Report,<br/><br/>")

                .append("<b style='font-weight:700'>Your Recommended Myrtle Pathway</b><br/>")
                .append("You are a ").append(persona).append(".<br/><br/>")

                .append(result)
                .append("</p>")
                .append("</td>")
                .append("</tr>")

                // PROFILE INDICATORS
                .append("<tr>")
                .append("<td style='padding-bottom:20px'>")
                .append("<p style='font-weight:300;margin:0 0 10px 0'>Your profile indicates:</p>")

                .append("<ul style='list-style:disc;padding-left:20px;font-weight:300;line-height:30px;margin:0;'>")
                .append("<li>Mature financial perspective</li>")
                .append("<li>Very steady income foundation</li>")
                .append("<li>High savings discipline</li>")
                .append("<li>Advanced investment approach</li>")
                .append("</ul><br/>")

                .append("<p style='font-weight:300;margin:0'>Advisory Focus:</p>")
                .append("<p style='font-weight:300;margin:0 0 10px 0'>Optimize efficiency:</p>")
                .append("<p style='font-weight:300;margin:0'>Estate planning, diversification, philanthropy, Everyday Family Office.<br/><br/>")
                .append("I would like to connect you with our Senior Wealth Manager, who specializes in legacy planning and wealth preservation strategies.")
                .append("</p>")
                .append("</td>")
                .append("</tr>")

                // FOOTER
                .append("<tr>")
                .append("<td style='background-color:#f5f8ff;padding:20px;border-radius:4px;'>")
                .append("<p style='font-weight:300;margin:0;'>")

                .append("Need help? <a href='#' style='text-decoration:underline;color:#42995a;font-weight:500;'>Contact our support team</a> ")
                .append("or no longer interested in our newsletters? ")
                .append("<a href='#' style='text-decoration:underline;color:#42995a;font-weight:500;'>Unsubscribe here</a>. ")
                .append("Want to give us feedback? Let us know what you think on our ")
                .append("<a href='#' style='text-decoration:underline;color:#42995a;font-weight:500;'>feedback site.</a>")

                .append("<div style='display:flex;justify-content:center;margin-top:30px;'>")
                .append("<div style='display:flex;gap:20px;'>")

                .append("<a href='https://facebook.com/' target='_blank'><img src='https://img.icons8.com/ios-filled/50/d2d2d2/facebook-new.png' width='28' style='display:block;'/></a>")
                .append("<a href='https://instagram.com/' target='_blank'><img src='https://img.icons8.com/ios-filled/50/d2d2d2/instagram-new.png' width='28' style='display:block;'/></a>")
                .append("<a href='https://linkedin.com/' target='_blank'><img src='https://img.icons8.com/ios-filled/50/d2d2d2/linkedin.png' width='28' style='display:block;'/></a>")
                .append("<a href='https://whatsapp.com/' target='_blank'><img src='https://img.icons8.com/ios-filled/50/d2d2d2/whatsapp.png' width='28' style='display:block;'/></a>")

                .append("</div></div></p>")
                .append("</td>")
                .append("</tr>")

                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");

        log.info("Sending survey response email | to={} | persona={}", to, persona);
        externalAPIs.sendTransactionalEmail(to, subject, html.toString());
    }

    public void sendSurveyResponseMailWeb(
            String to,
            String fullName,
            String gender,
            String persona,
            String result,
            double ebPercent,
            double saPercent,
            double pwnPercent
    ) {

        String subject = "New Survey Response from " + fullName;

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8' />")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0' />")
                .append("<title>Email Template</title>")
                .append("<link rel='preconnect' href='https://fonts.googleapis.com' />")
                .append("<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin />")
                .append("<link href='https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;500;600;700&display=swap' rel='stylesheet' />")
                .append("</head>")
                .append("<body style='margin:0;padding:20px 0;background:white;font-family: \"IBM Plex Sans\", Arial, sans-serif;'>")

                .append("<table width='100%' cellpadding='0' cellspacing='0' bgcolor='#ffffff' style='padding:20px'>")

                // TOP LOGOS
                .append("<tr>")
                .append("<td align='center' style='padding:20px 0;'>")
                .append("<table cellpadding='0' cellspacing='0' width='100%'>")
                .append("<tr>")

                .append("<td align='left' style='padding:0 10px;'>")
                .append("<img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557866/Cash_sopn3k.png' alt='Cash' style='display:block;'>")
                .append("</td>")

                .append("<td align='right' style='padding:0 10px;'>")
                .append("<img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png' alt='Myrtle' style='display:block;'>")
                .append("</td>")

                .append("</tr>")
                .append("</table>")
                .append("</td>")
                .append("</tr>")

                // MAIN CONTENT
                .append("<tr>")
                .append("<td align='left'>")
                .append("<table width='600' cellpadding='0' cellspacing='0' style='margin:0 auto;font-family:\"IBM Plex Sans\", Arial, sans-serif;'>")

                .append("<tr>")
                .append("<td style='padding:0 0 20px 0'>")
                .append("<h1 style='font-weight:600;margin:0;'>Myrtle Money Audit <br/> Report</h1>")
                .append("</td>")
                .append("</tr>")

                .append("<tr>")
                .append("<td style='padding-bottom:20px'>")
                .append("<p style='font-weight:300;line-height:28px;margin:0;'>")

                .append("<b style='font-weight:700'>Hello ").append(fullName).append(",</b><br/>")
                .append("Thank you for completing the Myrtle Money Audit! Here's your personalized Money Clarity Report.<br/><br/>")

                .append("<b style='font-weight:700'>Your Recommended Myrtle Pathway</b><br/>")
                .append("You are a <b>").append(persona).append("</b> persona.<br/><br/>")

                // PERCENTAGE SECTION ADDED HERE
                .append("<b style='font-weight:700;'>Your Persona Breakdown:</b><br/>")
                .append("<ul style='list-style:disc;padding-left:20px;font-weight:300;line-height:26px;'>")
                .append("<li><b>Everyday Builder:</b> ").append(String.format("%.1f%%", ebPercent)).append("</li>")
                .append("<li><b>Strategic Achiever:</b> ").append(String.format("%.1f%%", saPercent)).append("</li>")
                .append("<li><b>Private Wealth Niche:</b> ").append(String.format("%.1f%%", pwnPercent)).append("</li>")
                .append("</ul><br/>")

                .append(result)
                .append("</p>")
                .append("</td>")
                .append("</tr>")

                // ADVISORY SECTION
                .append("<tr>")
                .append("<td style='padding-bottom:20px'>")
                .append("<p style='font-weight:300;margin:0'>Advisory Focus:</p>")
                .append("<p style='font-weight:300;margin:0 0 10px 0'>Optimize efficiency:</p>")
                .append("<p style='font-weight:300;margin:0'>Estate planning, diversification, philanthropy, Everyday Family Office.<br/><br/>")
                .append("I would like to connect you with our Senior Wealth Manager, who specializes in legacy planning and wealth preservation strategies.")
                .append("</p>")
                .append("</td>")
                .append("</tr>")

                // FOOTER
                .append("<tr>")
                .append("<td style='background-color:#f5f8ff;padding:20px;border-radius:4px;'>")
                .append("<p style='font-weight:300;margin:0;'>")

                .append("Need help? <a href='#' style='text-decoration:underline;color:#42995a;font-weight:500;'>Contact our support team</a> ")
                .append("or no longer interested in our newsletters? ")
                .append("<a href='#' style='text-decoration:underline;color:#42995a;font-weight:500;'>Unsubscribe here</a>. ")
                .append("Want to give us feedback? Let us know what you think on our ")
                .append("<a href='#' style='text-decoration:underline;color:#42995a;font-weight:500;'>feedback site.</a>")

                .append("<div style='display:flex;justify-content:center;margin-top:30px;'>")
                .append("<div style='display:flex;gap:20px;'>")

                .append("<a href='https://facebook.com/' target='_blank'><img src='https://img.icons8.com/ios-filled/50/d2d2d2/facebook-new.png' width='28' style='display:block;'/></a>")
                .append("<a href='https://instagram.com/' target='_blank'><img src='https://img.icons8.com/ios-filled/50/d2d2d2/instagram-new.png' width='28' style='display:block;'/></a>")
                .append("<a href='https://linkedin.com/' target='_blank'><img src='https://img.icons8.com/ios-filled/50/d2d2d2/linkedin.png' width='28' style='display:block;'/></a>")
                .append("<a href='https://whatsapp.com/' target='_blank'><img src='https://img.icons8.com/ios-filled/50/d2d2d2/whatsapp.png' width='28' style='display:block;'/></a>")

                .append("</div></div></p>")
                .append("</td>")
                .append("</tr>")

                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");

        log.info("Sending web survey response email | to={} | persona={}", to, persona);
        externalAPIs.sendTransactionalEmail2(to, subject, html.toString(), "verify-me@myrtleng.com");
    }


    public void sendInvestmentTerminationRequestMail(
            String to,
            User user,
            WithdrawalAccount withdrawalAccount,
            Product investment,
            double amountToTerminate
    ) {

        String subject = "Investment Termination Request - "
                + user.getFirstName() + " " + user.getLastName();

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8' />")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0' />")
                .append("<title>Investment Termination Request</title>")
                .append("<style>")
                .append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }")
                .append(".container { padding: 20px; background: #ffffff; border-radius: 8px; }")
                .append(".header { font-size: 18px; font-weight: bold; margin-bottom: 10px; color:#2c3e50; }")
                .append(".label { font-weight: bold; }")
                .append(".box { padding: 15px; border: 1px solid #eee; border-radius: 6px; background: #fafafa; }")
                .append("</style>")
                .append("</head>")
                .append("<body>")
                .append("<div class='container'>")

                // TITLE
                .append("<div class='header'>Investment Termination Request</div>")
                .append("<p>A customer has requested the termination of an investment. Details are outlined below:</p>")

                // USER DETAILS
                .append("<div class='header'>Customer Details</div>")
                .append("<div class='box'>")
                .append("<p><span class='label'>Full Name:</span> ")
                .append(user.getFirstName()).append(" ").append(user.getLastName()).append("</p>")
                .append("<p><span class='label'>Email Address:</span> ")
                .append(user.getEmailAddress()).append("</p>")
                .append("<p><span class='label'>Investment Account ID:</span> ")
                .append(user.getInvestmentAccountId()).append("</p>")
                .append("</div><br/>")

                // ACCOUNT DETAILS
                .append("<div class='header'>Settlement Account Details</div>")
                .append("<div class='box'>")
//                .append("<p><span class='label'>Bank Name:</span> OCTOPUS MFB</p>")
                .append("<p><span class='label'>Bank Name:</span> ")
                .append(withdrawalAccount.getBankName() != null ? withdrawalAccount.getBankName() : "N/A")
                .append("</p>")

                .append("<p><span class='label'>Account Name:</span> ")
                .append(withdrawalAccount.getAccountName() != null ? withdrawalAccount.getAccountName() : "N/A")
                .append("</p>")

                .append("<p><span class='label'>Account Number:</span> ")
                .append(withdrawalAccount.getAccountNumber() != null ? withdrawalAccount.getAccountNumber() : "N/A")
                .append("</p>")
                .append("</div><br/>")

                // INVESTMENT DETAILS
                .append("<div class='header'>Investment Details</div>")
                .append("<div class='box'>")
                .append("<p><span class='label'>Product:</span> ")
                .append(investment.getLabel()).append("</p>")
                .append("<p><span class='label'>SEC ID:</span> ")
                .append(investment.getCode()).append("</p>")
                .append("<p><span class='label'>Currency:</span> ")
                .append(investment.getCurrency()).append("</p>")
                .append("</div><br/>")

                // TERMINATION DETAILS
                .append("<div class='header'>Termination Request</div>")
                .append("<div class='box'>")
                .append("<p><span class='label'>Amount Requested for Termination:</span> ")
                .append(String.format("%.2f", amountToTerminate)).append("</p>")
                .append("</p>")
                .append("</div><br/>")

                // FOOTER
                .append("<p>Please process this termination request via the backend system. ")
                .append("For any clarification, kindly reach out to the customer directly.</p>")
                .append("<p>Regards,<br/><strong>Myrtle Middleware System</strong></p>")

                .append("</div>")
                .append("</body>")
                .append("</html>");

        log.info("Sending investment termination request email | to={} | user={} | product={} | amount={}",
                to, user.getEmailAddress(), investment.getLabel(), amountToTerminate);
        externalAPIs.sendTransactionalEmail(to, subject, html.toString());
    }


    /**
     * Terminate Bulk Investment
     */

    public void sendBulkInvestmentTerminationMail(
            String to,
            User user,
            String productName,
            List<Investment> investments,
            BigDecimal totalAmount
    ) {

        String subject = "Bulk Investment Termination Request - "
                + user.getFirstName() + " " + user.getLastName();

        StringBuilder investmentRows = new StringBuilder();

        // Build table rows dynamically
        for (Investment inv : investments) {
            BigDecimal value = BigDecimal.ZERO;

            try {
                BigDecimal qty = new BigDecimal(
                        inv.getRequestedQty() == null ? "0" : inv.getRequestedQty()
                );
                BigDecimal price = new BigDecimal(
                        inv.getPrice() == null ? "0" : inv.getPrice()
                );
                value = qty.multiply(price);
            } catch (Exception ignored) {
            }

            investmentRows.append("<tr>")
                    .append("<td>").append(inv.getProduct().getLabel()).append("</td>")
                    .append("<td>").append(inv.getMarketCode()).append("</td>")
                    .append("<td>").append(inv.getSecId()).append("</td>")
                    .append("<td>").append(inv.getRequestedQty()).append("</td>")
                    .append("<td>").append(inv.getPrice()).append("</td>")
                    .append("<td>NGN ").append(value.toPlainString()).append("</td>")
                    .append("</tr>");
        }

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8' />")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0' />")
                .append("<title>Bulk Investment Termination Request</title>")
                .append("<style>")
                .append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }")
                .append(".container { padding: 20px; background: #ffffff; border-radius: 8px; }")
                .append(".header { font-size: 18px; font-weight: bold; margin-bottom: 10px; color:#2c3e50; }")
                .append(".label { font-weight: bold; }")
                .append(".box { padding: 15px; border: 1px solid #eee; border-radius: 6px; background: #fafafa; }")
                .append("table { width: 100%; border-collapse: collapse; margin-top: 10px; }")
                .append("th, td { border: 1px solid #eee; padding: 10px; text-align: left; }")
                .append("th { background: #f4f6f8; }")
                .append(".total { font-size: 16px; font-weight: bold; color: #8f0090; }")
                .append("</style>")
                .append("</head>")
                .append("<body>")
                .append("<div class='container'>")

                // TITLE
                .append("<div class='header'>Bulk Investment Termination Request</div>")
                .append("<p>A customer has requested termination of ALL active investments under a product.</p>")

                // CUSTOMER DETAILS
                .append("<div class='header'>Customer Details</div>")
                .append("<div class='box'>")
                .append("<p><span class='label'>Full Name:</span> ")
                .append(user.getFirstName()).append(" ").append(user.getLastName()).append("</p>")
                .append("<p><span class='label'>Email Address:</span> ")
                .append(user.getEmailAddress()).append("</p>")
                .append("<p><span class='label'>Account Number:</span> ")
                .append(user.getAccountNumber() != null ? user.getAccountNumber() : "N/A")
                .append("</p>")
                .append("</div><br/>")

                // PRODUCT SUMMARY
                .append("<div class='header'>Termination Summary</div>")
                .append("<div class='box'>")
                .append("<p><span class='label'>Product:</span> ").append(productName).append("</p>")
                .append("<p><span class='label'>Total Investments Count:</span> ")
                .append(investments.size()).append("</p>")
                .append("<p class='total'><span class='label'>Total Amount to Terminate:</span> NGN ")
                .append(totalAmount.toPlainString()).append("</p>")
                .append("</div><br/>")

                // INVESTMENT TABLE
                .append("<div class='header'>Investment Breakdown</div>")
                .append("<table>")
                .append("<thead>")
                .append("<tr>")
                .append("<th>Product</th>")
                .append("<th>Market Code</th>")
                .append("<th>SEC ID</th>")
                .append("<th>Quantity</th>")
                .append("<th>Price</th>")
                .append("<th>Value (NGN)</th>")
                .append("</tr>")
                .append("</thead>")
                .append("<tbody>")
                .append(investmentRows.toString())
                .append("</tbody>")
                .append("</table><br/>")

                // FOOTER
                .append("<p>Please process this bulk termination request via the admin backend system. ")
                .append("This request affects multiple active investments and requires operational approval.</p>")
                .append("<p>Regards,<br/><strong>Myrtle Middleware System</strong></p>")

                .append("</div>")
                .append("</body>")
                .append("</html>");

        log.info("Sending bulk investment termination email | to={} | user={} | product={} | count={} | totalAmount={}",
                to, user.getEmailAddress(), productName, investments.size(), totalAmount);
        externalAPIs.sendTransactionalEmail(to, subject, html.toString());
    }


    public void sendMailToCustomerCare(String to, EditUserRequest editUserRequest, String token) {
        String subject = "User Edit Request - " + editUserRequest.firstname() + " " + editUserRequest.lastname();

        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family:Arial,sans-serif;color:#333;line-height:1.6;'>")
                .append("<h2 style='color:#2c3e50;'>User Edit Request</h2>")
                .append("<p><strong>Name:</strong> ").append(editUserRequest.firstname()).append(" ").append(editUserRequest.lastname()).append("</p>")
                .append("<p><strong>Reason:</strong> ").append(editUserRequest.reason()).append("</p>");

        if (editUserRequest.imageProof() != null && editUserRequest.imageProof().length > 0) {
            html.append("<p><strong>Proof Images:</strong></p><div style='display:flex;flex-wrap:wrap;gap:10px;'>");
            for (String url : editUserRequest.imageProof()) {
                html.append("<a href='").append(url).append("' target='_blank'>")
                        .append("<img src='").append(url).append("' alt='Proof Image' style='max-width:280px;border:1px solid #ddd;border-radius:8px;margin:5px;'/>")
                        .append("</a>");
            }
            html.append("</div>");
        } else {
            html.append("<p><em>No proof images provided.</em></p>");
        }

        String approveUrl = host + "/api/v1/auth/approve?token=" + token;
        String declineUrl = host + "/api/v1/auth/decline?token=" + token;

        html.append("<p><strong>Action:</strong></p>")
                .append("<div>")
                .append("<a href='").append(approveUrl).append("' style='background-color:#4CAF50;color:white;padding:10px 20px;text-decoration:none;border-radius:5px;margin-right:10px;'>Approve</a>")
                .append("<a href='").append(declineUrl).append("' style='background-color:#f44336;color:white;padding:10px 20px;text-decoration:none;border-radius:5px;'>Decline</a>")
                .append("</div>")
                .append("</div>");

        log.info("Sending user edit request email to customer care | to={} | subject={}", to, subject);
        externalAPIs.sendTransactionalEmail(to, subject, html.toString());
    }


    public void sendMailToCustomerCareUpgrade(String to, Tier3UpgradePayLoad tier3UpgradePayLoad, String token, User user) {
        String subject = "Tier 3 Upgrade Request - Address Verification for " + user.getFirstName() + " " + user.getLastName();

        StringBuilder html = new StringBuilder();
        html.append("<h2>Tier 3 Upgrade Request</h2>")
                .append("<p><strong>Customer's Name:</strong> ").append(user.getFirstName()).append(" ").append(user.getLastName()).append("</p>")
                .append("<p><strong>Customer's email:</strong> ").append(user.getEmailAddress()).append("</p>")
                .append("<p><strong>Customer's phone:</strong> ").append(user.getPhoneNumber()).append("</p>")
                .append("<p><strong>Requested Address:</strong> ").append(tier3UpgradePayLoad.address()).append("</p>")
                .append("<p><strong>Proof Type:</strong> ").append(tier3UpgradePayLoad.proofType()).append("</p>");

        if (tier3UpgradePayLoad.proof() != null && tier3UpgradePayLoad.proof().length > 0) {
            html.append("<p><strong>Attached Proof Documents:</strong></p>");
            for (String url : tier3UpgradePayLoad.proof()) {
                html.append("<a href='").append(url).append("' target='_blank'>")
                        .append("<img src='").append(url).append("' alt='Proof Document' style='max-width:300px;border:1px solid #ddd;border-radius:5px;margin-bottom:10px;'/>")
                        .append("</a>");
            }
        } else {
            html.append("<p><em>No proof documents provided.</em></p>");
        }

        String approveUrl = host + "/api/v1/auth/tier3/approve?token=" + token;
        String declineUrl = host + "/api/v1/auth/tier3/decline?token=" + token;

        html.append("<p><strong>Action:</strong></p>")
                .append("<a href='").append(approveUrl).append("' style='background-color:#4CAF50;color:white;padding:10px 20px;text-decoration:none;border-radius:5px;'>Approve</a>")
                .append("&nbsp;")
                .append("<a href='").append(declineUrl).append("' style='background-color:#f44336;color:white;padding:10px 20px;text-decoration:none;border-radius:5px;'>Decline</a>");

        log.info("Sending Tier 3 upgrade request email to customer care | to={} | user={}", to, user.getEmailAddress());
        externalAPIs.sendTransactionalEmail(to, subject, html.toString());
    }

    public void sendWalletWebhookNotification(User user, Transaction transaction, double newBalance) {
        String subject = (transaction.getAction() == TransactionAction.CREDIT ? "Wallet Credit Alert" : "Wallet Debit Alert") + " | Myrtle Assets Management";

        DecimalFormat formatter = new DecimalFormat("#,##0.00");
        String amount = "NGN " + formatter.format(transaction.getAmount());
        String balance = "NGN " + formatter.format(newBalance);

        String accent = transaction.getAction() == TransactionAction.CREDIT ? "#0f9d58" : "#c0392b";
        String gradient = transaction.getAction() == TransactionAction.CREDIT
                ? "linear-gradient(135deg,#0f9d58,#0b7c4a)"
                : "linear-gradient(135deg,#c0392b,#96281b)";
        String badge = transaction.getAction() == TransactionAction.CREDIT ? "Credit" : "Debit";
        String headline = transaction.getAction() == TransactionAction.CREDIT
                ? "Funds received into your wallet"
                : "Funds moved from your wallet";

        String reference = transaction.getTxRef() != null ? transaction.getTxRef() : "N/A";
        String narration = transaction.getNarration() != null ? transaction.getNarration() : "Wallet transaction";
        String source = transaction.getSource() != null ? transaction.getSource() : "Myrtle";
        String when = transaction.getCreatedAt() != null
                ? transaction.getCreatedAt().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm"));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                .append("<title>Myrtle Wallet Alert</title>")
                .append("<link rel='preconnect' href='https://fonts.googleapis.com'>")
                .append("<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>")
                .append("<link href='https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap' rel='stylesheet'>")
                .append("</head>")
                .append("<body style='margin:0;padding:0;background:#f7f9fb;font-family:\"Plus Jakarta Sans\", Arial, sans-serif;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' bgcolor='#f7f9fb' style='padding:24px 0;'>")

                .append("<tr><td align='center'>")
                .append("<table width='640' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 18px 38px rgba(16,24,40,0.08);'>")

                .append("<tr><td style='padding:16px 24px;border-bottom:1px solid #eef2f7;'>")
                .append("<table width='100%'><tr>")
                .append("<td align='left'><img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png' alt='Myrtle' height='38' style='display:block;'></td>")
                .append("<td align='right' style='color:#98a2b3;font-size:13px;'>Secure wallet notification</td>")
                .append("</tr></table>")
                .append("</td></tr>")

                .append("<tr><td style='padding:0 24px 8px 24px;'>")
                .append("<div style='padding:28px 24px;margin-top:18px;background:").append(gradient).append(";border-radius:14px;color:#ffffff;'>")
                .append("<div style='font-size:13px;letter-spacing:0.5px;text-transform:uppercase;opacity:0.9;'>Wallet ").append(badge).append(" Alert</div>")
                .append("<div style='font-size:28px;font-weight:700;margin-top:6px;'>").append(amount).append("</div>")
                .append("<div style='margin-top:10px;font-size:16px;line-height:24px;'>").append(headline).append("</div>")
                .append("<div style='margin-top:14px;display:inline-flex;align-items:center;padding:10px 14px;background:rgba(255,255,255,0.14);border-radius:12px;font-weight:600;'>")
                .append("<span style='width:10px;height:10px;border-radius:50%;background:#ffffff;opacity:0.8;margin-right:10px;display:inline-block'></span>")
                .append("Ref: ").append(reference)
                .append("</div>")
                .append("</div>")
                .append("</td></tr>")

                .append("<tr><td style='padding:6px 24px 24px 24px;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;'>")

                .append("<tr>")
                .append("<td style='padding:8px 0;color:#101828;font-weight:600;'>Hi ").append(user.getFirstName()).append(",</td>")
                .append("</tr>")

                .append("<tr>")
                .append("<td style='padding:0 0 14px 0;color:#475467;font-size:14px;line-height:22px;'>")
                .append("Here are the details of your latest wallet transaction.")
                .append("</td>")
                .append("</tr>")

                .append("<tr>")
                .append("<td>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='font-size:14px;color:#101828;'>")

                .append("<tr><td style='padding:10px 0;color:#475467;'>Type</td><td align='right' style='font-weight:600;color:").append(accent).append("'>").append(badge).append("</td></tr>")
                .append("<tr><td style='padding:10px 0;color:#475467;'>Amount</td><td align='right' style='font-weight:700;'>").append(amount).append("</td></tr>")
                .append("<tr><td style='padding:10px 0;color:#475467;'>New balance</td><td align='right' style='font-weight:700;'>").append(balance).append("</td></tr>")
                .append("<tr><td style='padding:10px 0;color:#475467;'>Narration</td><td align='right' style='color:#101828;font-weight:500;'>").append(narration).append("</td></tr>")
                .append("<tr><td style='padding:10px 0;color:#475467;'>Source</td><td align='right' style='color:#101828;font-weight:500;'>").append(source).append("</td></tr>")
                .append("<tr><td style='padding:10px 0;color:#475467;'>Date</td><td align='right' style='color:#101828;font-weight:500;'>").append(when).append("</td></tr>")
                .append("<tr><td style='padding:10px 0;color:#475467;'>Reference</td><td align='right' style='color:#101828;font-weight:500;'>").append(reference).append("</td></tr>")

                .append("</table>")
                .append("</td>")
                .append("</tr>")

                .append("</table>")
                .append("</td></tr>")

                .append("<tr><td style='padding:0 24px 24px 24px;color:#98a2b3;font-size:12px;line-height:18px;'>")
                .append("This message was sent to ").append(user.getEmailAddress()).append(". If you did not initiate this transaction, please contact support immediately.")
                .append("</td></tr>")

                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");

        log.info("Sending wallet {} notification email | to={} | amount={} | newBalance={}",
                transaction.getAction(), user.getEmailAddress(), transaction.getAmount(), newBalance);
        externalAPIs.sendTransactionalEmail(user.getEmailAddress(), subject, html.toString());
    }

    public void sendInvestmentBookingNotification(
            User user,
            Product product,
            String documentImageUrl,
            String refCode
    ) {

        String when = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm"));

        String subject = "Dollar Shield Investment Submission | " + product.getLabel();

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                .append("<title>Investment Submission</title>")
                .append("<link href='https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap' rel='stylesheet'>")
                .append("</head>")
                .append("<body style='margin:0;padding:0;background:#f7f9fb;font-family:\"Plus Jakarta Sans\", Arial, sans-serif;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='padding:24px 0;'>")

                // Card
                .append("<tr><td align='center'>")
                .append("<table width='680' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:18px;box-shadow:0 18px 38px rgba(16,24,40,0.08);overflow:hidden;'>")

                // Header
                .append("<tr><td style='padding:18px 24px;border-bottom:1px solid #eef2f7;'>")
                .append("<table width='100%'><tr>")
                .append("<td><img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png' height='36'></td>")
                .append("<td align='right' style='font-size:13px;color:#98a2b3;'>Operations Notification</td>")
                .append("</tr></table>")
                .append("</td></tr>")

                // Banner
                .append("<tr><td style='padding:24px;'>")
                .append("<div style='background:linear-gradient(135deg,#1f6fe5,#174ea6);color:#fff;padding:26px;border-radius:14px;'>")
                .append("<div style='font-size:13px;text-transform:uppercase;opacity:0.9;'>Investment Submission</div>")
                .append("<div style='font-size:26px;font-weight:700;margin-top:6px;'>")
                .append(product.getLabel())
                .append("</div>")
                .append("<div style='margin-top:10px;font-size:15px;'>Dollar Shield Product</div>")
                .append("</div>")
                .append("</td></tr>")

                // Investor Details
                .append("<tr><td style='padding:0 24px 24px 24px;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;font-size:14px;'>")
                .append(row("Investor Name", user.getFirstName() + " " + user.getLastName()))
                .append(row("Email Address", user.getEmailAddress()))
                .append(row("Product", product.getLabel()))
                .append(row("Ref Code", refCode))
                .append(row("Submitted At", when))
                .append("</table>")
                .append("</td></tr>")

                // Document Preview (Image)
                .append("<tr><td style='padding:0 24px 24px 24px;'>")
                .append("<div style='font-weight:600;color:#101828;margin-bottom:10px;'>Submitted Investment Document</div>")
                .append("<div style='border:1px solid #e4e7ec;border-radius:14px;overflow:hidden;'>")
                .append("<img src='").append(documentImageUrl).append("' ")
                .append("style='width:100%;max-height:420px;object-fit:contain;background:#f9fafb;'>")
                .append("</div>")
                .append("</td></tr>")

                // Footer
                .append("<tr><td style='padding:0 24px 24px 24px;font-size:12px;color:#98a2b3;'>")
                .append("This email is for internal visibility only. The attached document is provided for operational reference. ")
                .append("No action is required from this notification.")
                .append("</td></tr>")

                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");

        log.info("Sending investment booking notification email | user={} | product={} | refCode={}",
                user.getEmailAddress(), product.getLabel(), refCode);
        externalAPIs.sendTransactionalEmail(
                "wecare@myrtleng.com",
                subject,
                html.toString()
        );
    }


    private String row(String label, String value) {
        return "<tr>"
                + "<td style='padding:10px 0;color:#475467;'>" + label + "</td>"
                + "<td align='right' style='font-weight:600;color:#101828;'>" + (value != null ? value : "—") + "</td>"
                + "</tr>";
    }

    /**
     * Sends a confirmation email for admin-triggered account/KYC actions.
     * Uses transactional email via {@link ExternalAPIs#sendTransactionalEmail(String, String, String)}.
     */
    public void sendAdminNotificationEmail(String to, String subject, String headline, String bodyHtml) {
        String safeHeadline = headline == null ? "Account update" : escapeHtml(headline);
        String safeBodyHtml = bodyHtml == null ? "" : bodyHtml;

        String html = """
                <!doctype html>
                <html lang='en'>
                  <head>
                    <meta charset='UTF-8' />
                    <meta name='viewport' content='width=device-width, initial-scale=1.0' />
                    <title>Myrtle — Notification</title>
                  </head>
                  <body style='margin:0;padding:0;font-family:Mulish,Segoe UI,sans-serif;background:#f2f5f2;'>
                    <div style='max-width:560px;margin:0 auto;padding:24px;'>
                      <div style='background:#fff;border:1px solid #e4e7ec;border-radius:12px;padding:28px;'>
                        <div style='color:#42995a;font-weight:700;font-size:18px;margin-bottom:16px;'>Myrtle</div>
                        <h1 style='font-size:20px;color:#101828;margin:0 0 12px 0;'>%s</h1>
                        <div style='font-size:15px;color:#475467;line-height:1.6;'>%s</div>
                        <p style='font-size:13px;color:#98a2b3;margin-top:24px;'>
                          If you did not expect this change, contact wecare@myrtleng.com immediately.
                        </p>
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(safeHeadline, safeBodyHtml);

        log.info("Sending admin notification email | to={} | subject={}", to, subject);
        externalAPIs.sendTransactionalEmail(to, subject, html);
    }

    public void notifyCustomerServiceForNewUser(User savedUser, List<NextOfKinRequest> nextOfKins) {

        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm"));
        String subject = savedUser.getFirstName() + " " + savedUser.getLastName() + " — New account registration";

        String fullName = savedUser.getFirstName() + " "
                + (savedUser.getMiddleName() != null && !savedUser.getMiddleName().isBlank()
                ? savedUser.getMiddleName().trim() + " "
                : "")
                + savedUser.getLastName();

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                .append("<title>New account registration</title>")
                .append("<link href='https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap' rel='stylesheet'>")
                .append("</head>")
                .append("<body style='margin:0;padding:0;background:#f7f9fb;font-family:\"Plus Jakarta Sans\", Arial, sans-serif;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='padding:24px 0;'>")

                .append("<tr><td align='center'>")
                .append("<table width='680' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:18px;box-shadow:0 18px 38px rgba(16,24,40,0.08);overflow:hidden;'>")

                .append("<tr><td style='padding:18px 24px;border-bottom:1px solid #eef2f7;'>")
                .append("<table width='100%'><tr>")
                .append("<td><img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png' height='36'></td>")
                .append("<td align='right' style='font-size:13px;color:#98a2b3;'>Operations Notification</td>")
                .append("</tr></table>")
                .append("</td></tr>")

                .append("<tr><td style='padding:24px;'>")
                .append("<div style='background:linear-gradient(135deg,#1f6fe5,#174ea6);color:#fff;padding:26px;border-radius:14px;'>")
                .append("<div style='font-size:13px;text-transform:uppercase;opacity:0.9;'>New account registration</div>")
                .append("<div style='font-size:26px;font-weight:700;margin-top:6px;'>")
                .append(esc(fullName.trim()))
                .append("</div>")
                .append("<div style='margin-top:10px;font-size:15px;'>")
                .append(esc(savedUser.getEmailAddress()))
                .append("</div>")
                .append("</div>")
                .append("</td></tr>")

                .append("<tr><td style='padding:0 24px 24px 24px;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;font-size:14px;'>")
                .append(row("Full name", esc(fullName.trim())))
                .append(row("Email address", esc(savedUser.getEmailAddress())))
                .append(row("BVN", esc(savedUser.getBvn())))
                .append(row("NIN", esc(savedUser.getNin())))
                .append(row("Address", esc(savedUser.getAddress())))
                .append(row("Registered at", esc(when)))
                .append("</table>")
                .append("</td></tr>");

        html.append("<tr><td style='padding:0 24px 24px 24px;'>")
                .append("<div style='font-weight:600;color:#101828;margin-bottom:14px;font-size:15px;'>Next of kin</div>");

        if (nextOfKins != null && !nextOfKins.isEmpty()) {
            for (int i = 0; i < nextOfKins.size(); i++) {
                NextOfKinRequest kin = nextOfKins.get(i);
                int num = i + 1;
                html.append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;font-size:14px;margin-bottom:16px;'>")
                        .append("<tr><td colspan='2' style='padding:0 0 12px 0;font-weight:600;color:#42995a;font-size:13px;'>Contact ")
                        .append(num)
                        .append("</td></tr>")
                        .append(row("Full name", esc(kin.fullName())))
                        .append(row("Relationship", esc(kin.relationship())))
                        .append(row("Phone", esc(kin.phoneNumber())))
                        .append(row("Email", esc(kin.email())))
                        .append(row("Address", esc(kin.address())))
                        .append(row("Primary", esc(Boolean.TRUE.equals(kin.isPrimary()) ? "Yes" : "No")))
                        .append("</table>");
            }
        } else {
            html.append("<p style='margin:0;font-size:14px;color:#475467;'>No next of kin provided at registration.</p>");
        }

        html.append("</td></tr>")

                .append("<tr><td style='padding:0 24px 24px 24px;font-size:12px;color:#98a2b3;'>")
                .append("This email is for internal visibility only. It confirms a new user account was created on the Myrtle platform.")
                .append("</td></tr>")

                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");

        log.info("Sending new user registration notification to customer service | user={} | nextOfKinCount={}",
                savedUser.getEmailAddress(), nextOfKins != null ? nextOfKins.size() : 0);
        externalAPIs.sendTransactionalEmail(
                "wecare@myrtleng.com",
                subject,
                html.toString()
        );
    }

    public void sendAbandonedOnboardingReminder(PendingOnboarding record) {
        String logoUrl = "https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png";
        String firstName = record.getFirstName() == null || record.getFirstName().isBlank()
                ? "there"
                : escapeHtml(record.getFirstName().trim());
        String appUrl = frontendUrl == null || frontendUrl.isBlank() ? host : frontendUrl.trim();
        String safeContinue = escapeHtml(appUrl);
        String safeSupportEmail = escapeHtml(supportEmail == null || supportEmail.isBlank()
                ? "wecare@myrtleng.com"
                : supportEmail);
        String phoneLine = supportPhone == null || supportPhone.isBlank()
                ? ""
                : "<br /><br />You can also call us on <strong>" + escapeHtml(supportPhone) + "</strong>.";

        String html = """
                <!doctype html>
                <html lang='en'>
                  <head>
                    <meta charset='UTF-8' />
                    <meta name='viewport' content='width=device-width, initial-scale=1.0' />
                    <title>Myrtle – Continue your account opening</title>
                    <link
                      href='https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=DM+Sans:wght@300;400;500;600&display=swap'
                      rel='stylesheet'
                    />
                  </head>
                  <body
                    style='
                      background-color: #f2f5f2;
                      font-family: "DM Sans", sans-serif;
                      padding: 40px 16px;
                    '
                  >
                    <div style='max-width: 560px; margin: 0 auto'>
                      <div
                        style='
                          background-color: #42995a;
                          border-radius: 16px 16px 0 0;
                          padding: 28px 40px;
                          display: flex;
                          align-items: center;
                          gap: 12px;
                        '
                      >
                        <div></div>
                        <div>
                          <img src='__LOGO_URL__' alt='Myrtle Logo' width='120' height='32' />
                        </div>
                      </div>

                      <div
                        style='
                          background: #ffffff;
                          padding: 48px 40px 40px;
                          border-left: 1px solid #e8ede8;
                          border-right: 1px solid #e8ede8;
                        '
                      >
                        <p
                          style='
                            font-size: 11px;
                            font-weight: 600;
                            letter-spacing: 2px;
                            text-transform: uppercase;
                            color: #42995a;
                            margin-bottom: 12px;
                          '
                        >
                          Account opening
                        </p>

                        <h1
                          style='
                            font-family: "DM Serif Display", serif;
                            font-size: 28px;
                            color: #1a2e1a;
                            line-height: 1.25;
                            margin-bottom: 16px;
                          '
                        >
                          Your Myrtle account opening<br /><em style='color: #42995a'>is still in progress</em>
                        </h1>

                        <p
                          style='
                            font-size: 15px;
                            color: #5a6b5a;
                            line-height: 1.7;
                            margin-bottom: 32px;
                          '
                        >
                          Hello __FIRST_NAME__,<br /><br />
                          You started opening a Myrtle account but have not finished creating it yet.
                          Open the Myrtle app and continue from where you left off — we have kept your progress.
                          __PHONE_LINE__
                        </p>

                        <div
                          style='
                            background: #f7fbf7;
                            border: 1.5px solid #c8e6cc;
                            border-radius: 14px;
                            padding: 28px 24px;
                            text-align: left;
                            margin-bottom: 32px;
                          '
                        >
                          <p
                            style='
                              font-size: 13px;
                              font-weight: 600;
                              letter-spacing: 1px;
                              color: #42995a;
                              margin-bottom: 14px;
                            '
                          >
                            WHAT YOU CAN DO NEXT
                          </p>

                          <ul
                            style='
                              padding-left: 18px;
                              color: #1a2e1a;
                              font-size: 14px;
                              line-height: 1.8;
                            '
                          >
                            <li>Return to the Myrtle app with the email you started with</li>
                            <li>Continue from the step you last completed</li>
                            <li>Finish creating your account when you are ready</li>
                          </ul>

                          <p style='text-align: center; margin: 20px 0 0 0;'>
                            <a
                              href='__CONTINUE_URL__'
                              style='
                                display: inline-block;
                                background: #42995a;
                                color: #ffffff;
                                text-decoration: none;
                                font-weight: 600;
                                padding: 12px 24px;
                                border-radius: 10px;
                              '
                            >Continue Application</a>
                          </p>
                        </div>

                        <div
                          style='
                            height: 1px;
                            background: linear-gradient(
                              to right,
                              transparent,
                              #e0eae0,
                              transparent
                            );
                            margin: 32px 0;
                          '
                        ></div>

                        <div
                          style='
                            background: #fafffe;
                            border-left: 3px solid #42995a;
                            border-radius: 0 8px 8px 0;
                            padding: 14px 18px;
                            margin-bottom: 28px;
                          '
                        >
                          <p style='font-size: 13px; color: #4a5e4a; line-height: 1.6'>
                            <strong style='color: #1a2e1a'>Didn't start this?</strong> If you did not
                            start an application with Myrtle, you can ignore this email.
                          </p>
                        </div>

                        <p style='font-size: 14px; color: #7a8e7a; line-height: 1.7'>
                          Need help? Email us at
                          <a href='mailto:__SUPPORT_EMAIL__' style='color: #42995a'>__SUPPORT_EMAIL__</a>.
                          <strong
                            style='
                              color: #1a2e1a;
                              display: block;
                              margin-top: 12px;
                              font-size: 15px;
                            '
                          >
                            The Myrtle Team 🌿
                          </strong>
                        </p>
                      </div>

                      <div
                        style='
                          background: #42995a;
                          border-radius: 0 0 16px 16px;
                          padding: 24px 40px;
                          text-align: center;
                          border-top: 3px solid #367d4a;
                        '
                      >
                        <p
                          style='
                            font-size: 12px;
                            color: rgba(255, 255, 255, 0.65);
                            line-height: 1.7;
                          '
                        >
                          <span
                            style='
                              font-family: "DM Serif Display", serif;
                              font-size: 14px;
                              color: white;
                            '
                            >Myrtle Asset Management & Trust Limited</span
                          ><br />
                          You're receiving this email because you started opening an account with
                          Myrtle.<br />
                          If you have questions, contact us at
                          <a
                            href='mailto:wecare@myrtleng.com'
                            style='color: rgba(255, 255, 255, 0.85); text-decoration: underline'
                          >
                            wecare@myrtleng.com
                          </a>
                        </p>

                        <p
                          style='
                            margin-top: 10px;
                            font-size: 12px;
                            color: rgba(255, 255, 255, 0.65);
                          '
                        >
                          &copy; __YEAR__ Myrtle. All rights reserved.
                        </p>
                      </div>
                    </div>
                  </body>
                </html>
                """;

        String body = html
                .replace("__LOGO_URL__", logoUrl)
                .replace("__FIRST_NAME__", firstName)
                .replace("__CONTINUE_URL__", safeContinue)
                .replace("__PHONE_LINE__", phoneLine)
                .replace("__SUPPORT_EMAIL__", safeSupportEmail)
                .replace("__YEAR__", String.valueOf(Year.now().getValue()));

        log.info("Sending abandoned onboarding reminder | to={}", record.getEmail());
        externalAPIs.sendTransactionalEmailStrict(
                record.getEmail(),
                "Continue Your Myrtle Account Opening",
                body
        );
    }

    public void sendAbandonedOnboardingInternalAlert(PendingOnboarding record) {
        DateTimeFormatter when = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm");
        String fullName = joinName(record.getFirstName(), record.getLastName());
        String lastCompleted = record.getLastCompletedStage() == null
                ? "—"
                : record.getLastCompletedStage().label();
        String current = record.getCurrentStage() == null
                ? "—"
                : record.getCurrentStage().label();
        String reason = record.getExitReason() == null ? "UNKNOWN" : record.getExitReason().name();
        String started = record.getStartedAt() == null ? "—" : record.getStartedAt().format(when);
        String lastActivity = record.getLastActivityAt() == null ? "—" : record.getLastActivityAt().format(when);
        String status = record.getStatus() == null ? "PENDING_ONBOARDING" : record.getStatus().name();
        String subject = fullName + " — Incomplete account opening";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                .append("<title>Incomplete account opening</title>")
                .append("<link href='https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap' rel='stylesheet'>")
                .append("</head>")
                .append("<body style='margin:0;padding:0;background:#f7f9fb;font-family:\"Plus Jakarta Sans\", Arial, sans-serif;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='padding:24px 0;'>")

                .append("<tr><td align='center'>")
                .append("<table width='680' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:18px;box-shadow:0 18px 38px rgba(16,24,40,0.08);overflow:hidden;'>")

                .append("<tr><td style='padding:18px 24px;border-bottom:1px solid #eef2f7;'>")
                .append("<table width='100%'><tr>")
                .append("<td><img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png' height='36'></td>")
                .append("<td align='right' style='font-size:13px;color:#98a2b3;'>Operations Notification</td>")
                .append("</tr></table>")
                .append("</td></tr>")

                .append("<tr><td style='padding:24px;'>")
                .append("<div style='background:linear-gradient(135deg,#1f6fe5,#174ea6);color:#fff;padding:26px;border-radius:14px;'>")
                .append("<div style='font-size:13px;text-transform:uppercase;opacity:0.9;'>Incomplete account opening</div>")
                .append("<div style='font-size:26px;font-weight:700;margin-top:6px;'>")
                .append(esc(fullName))
                .append("</div>")
                .append("<div style='margin-top:10px;font-size:15px;'>")
                .append(esc(record.getEmail()))
                .append("</div>")
                .append("</div>")
                .append("</td></tr>")

                .append("<tr><td style='padding:0 24px 24px 24px;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;font-size:14px;'>")
                .append(row("Full name", esc(fullName)))
                .append(row("Email address", esc(record.getEmail())))
                .append(row("Phone", esc(blankToDash(record.getPhoneNumber()))))
                .append(row("Onboarding type", esc(record.getOnboardingType() == null ? "—" : record.getOnboardingType().name())))
                .append(row("Last completed stage", esc(lastCompleted)))
                .append(row("Current stage", esc(current)))
                .append(row("Status", esc(status)))
                .append(row("Reason", esc(reason)))
                .append(row("Started at", esc(started)))
                .append(row("Last activity", esc(lastActivity)))
                .append("</table>")
                .append("</td></tr>")

                .append("<tr><td style='padding:0 24px 24px 24px;font-size:12px;color:#98a2b3;'>")
                .append("This email is for internal visibility only. It confirms this prospective customer has not completed account creation.")
                .append("</td></tr>")

                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");

        log.info("Sending internal abandoned onboarding alert | customer={}", record.getEmail());
        externalAPIs.sendTransactionalEmailStrict(
                onboardingInternalNotifyEmail,
                subject,
                html.toString()
        );
    }

    /**
     * Internal reminder after a customer clicks "I Have Made Payment".
     * This is a declaration only — it is not proof that funds were received.
     */
    public void sendInvestmentPaymentConfirmationReminder(
            User user,
            GeneralAccount receivingAccount,
            ManualTransactionEntity txn
    ) {
        String when = txn.getTxnTime() != null
                ? txn.getTxnTime().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm"));
        String fullName = joinName(user.getFirstName(), user.getLastName());
        if (user.getMiddleName() != null && !user.getMiddleName().isBlank()) {
            fullName = joinName(
                    user.getFirstName() + " " + user.getMiddleName().trim(),
                    user.getLastName()
            );
        }
        String productName = receivingAccount != null ? receivingAccount.getProductName() : null;
        String appUrl = frontendUrl == null || frontendUrl.isBlank() ? host : frontendUrl.trim();
        String to = supportEmail == null || supportEmail.isBlank()
                ? "wecare@myrtleng.com"
                : supportEmail;
        String subject = "Investment Payment Confirmation Required";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>")
                .append("<html lang='en'>")
                .append("<head>")
                .append("<meta charset='UTF-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>")
                .append("<title>Investment Payment Confirmation Required</title>")
                .append("<link href='https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700&display=swap' rel='stylesheet'>")
                .append("</head>")
                .append("<body style='margin:0;padding:0;background:#f7f9fb;font-family:\"Plus Jakarta Sans\", Arial, sans-serif;'>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='padding:24px 0;'>")

                .append("<tr><td align='center'>")
                .append("<table width='680' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:18px;box-shadow:0 18px 38px rgba(16,24,40,0.08);overflow:hidden;'>")

                .append("<tr><td style='padding:18px 24px;border-bottom:1px solid #eef2f7;'>")
                .append("<table width='100%'><tr>")
                .append("<td><img src='https://res.cloudinary.com/dbnoei7ty/image/upload/v1763557865/myrtle_lxwjbm.png' height='36'></td>")
                .append("<td align='right' style='font-size:13px;color:#98a2b3;'>Operations Notification</td>")
                .append("</tr></table>")
                .append("</td></tr>")

                .append("<tr><td style='padding:24px;'>")
                .append("<div style='background:linear-gradient(135deg,#1f6fe5,#174ea6);color:#fff;padding:26px;border-radius:14px;'>")
                .append("<div style='font-size:13px;text-transform:uppercase;opacity:0.9;'>Payment confirmation required</div>")
                .append("<div style='font-size:26px;font-weight:700;margin-top:6px;'>")
                .append(esc(fullName))
                .append("</div>")
                .append("<div style='margin-top:10px;font-size:15px;'>")
                .append("A customer has indicated that payment has been made.")
                .append("</div>")
                .append("</div>")
                .append("</td></tr>")

                .append("<tr><td style='padding:0 24px 16px 24px;font-size:15px;color:#344054;line-height:1.6;'>")
                .append("Kindly confirm receipt of this payment in your receiving bank. ")
                .append("If the payment is not received, please engage the customer.")
                .append("</td></tr>")

                .append("<tr><td style='padding:0 24px 24px 24px;'>")
                .append("<div style='font-weight:600;color:#101828;margin-bottom:10px;font-size:15px;'>Customer Details</div>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;font-size:14px;'>")
                .append(row("Name", esc(fullName)))
                .append(row("Email", esc(user.getEmailAddress())))
                .append(row("Phone", esc(blankToDash(user.getPhoneNumber()))))
                .append("</table>")
                .append("</td></tr>")

                .append("<tr><td style='padding:0 24px 24px 24px;'>")
                .append("<div style='font-weight:600;color:#101828;margin-bottom:10px;font-size:15px;'>Investment Details</div>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;font-size:14px;'>")
                .append(row("Investment", esc(blankToDash(productName))))
                .append(row("Amount", esc(blankToDash(txn.getTxnAmount()))))
                .append(row("Investment ID", "—"))
                .append(row("Transaction ID", esc(blankToDash(txn.getId()))))
                .append(row("Transaction Reference", esc(blankToDash(txn.getUserPhoneNumber()))))
                .append("</table>")
                .append("</td></tr>");

        if (receivingAccount != null) {
            html.append("<tr><td style='padding:0 24px 24px 24px;'>")
                    .append("<div style='font-weight:600;color:#101828;margin-bottom:10px;font-size:15px;'>Receiving Bank</div>")
                    .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;font-size:14px;'>")
                    .append(row("Bank", esc(blankToDash(receivingAccount.getBankName()))))
                    .append(row("Account name", esc(blankToDash(receivingAccount.getAccountName()))))
                    .append(row("Account number", esc(blankToDash(receivingAccount.getAccountNumber()))))
                    .append("</table>")
                    .append("</td></tr>");
        }

        html.append("<tr><td style='padding:0 24px 24px 24px;'>")
                .append("<div style='font-weight:600;color:#101828;margin-bottom:10px;font-size:15px;'>Payment Declaration</div>")
                .append("<table width='100%' cellpadding='0' cellspacing='0' style='border:1px solid #eef2f7;border-radius:12px;padding:18px;font-size:14px;'>")
                .append(row("Date/Time", esc(when)))
                .append(row("Status", "Customer indicated payment — not verified"))
                .append("</table>")
                .append("</td></tr>");

        if (appUrl != null && !appUrl.isBlank()) {
            html.append("<tr><td style='padding:0 24px 24px 24px;'>")
                    .append("<a href='").append(escapeHtml(appUrl)).append("' ")
                    .append("style='display:inline-block;background:#1f6fe5;color:#fff;text-decoration:none;padding:12px 18px;border-radius:10px;font-weight:600;font-size:14px;'>")
                    .append("View Customer")
                    .append("</a>")
                    .append("</td></tr>");
        }

        html.append("<tr><td style='padding:0 24px 24px 24px;font-size:12px;color:#98a2b3;'>")
                .append("This email is a reminder only. The customer's click is not proof that payment was received. ")
                .append("Please verify the payment in the receiving bank and take the appropriate action. ")
                .append("If the payment is not received, please engage the customer.")
                .append("</td></tr>")

                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");

        log.info("Sending investment payment confirmation reminder | customer={} | txnId={} | product={}",
                user.getEmailAddress(), txn.getId(), productName);
        externalAPIs.sendTransactionalEmail(to, subject, html.toString());
    }

    private String joinName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String joined = (first + " " + last).trim();
        return joined.isEmpty() ? "—" : joined;
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
