<#--
    Derived from Dokka 2.3.0's base.ftl. Copyright 2014-2024 JetBrains s.r.o.
    Licensed under the Apache License, Version 2.0. See ../LICENSE.txt and ../NOTICE.txt.
    Modified for libghostty-cmp to add the Demos section in the sidebar.
-->
<#import "includes/page_metadata.ftl" as page_metadata>
<#import "includes/header.ftl" as header>
<#import "includes/footer.ftl" as footer>
<!DOCTYPE html>
<html class="no-js" lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1" charset="UTF-8">
    <@page_metadata.display/>
    <@template_cmd name="pathToRoot">
        <script>var pathToRoot = "${pathToRoot}";</script></@template_cmd>
    <script>document.documentElement.classList.replace("no-js", "js");</script>
    <#-- Apply the saved theme before rendering the page. -->
    <script>const storage = localStorage.getItem("dokka-dark-mode")
      if (storage == null) {
        const osDarkSchemePreferred = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
        if (osDarkSchemePreferred === true) {
          document.getElementsByTagName("html")[0].classList.add("theme-dark")
        }
      } else {
        const savedDarkMode = JSON.parse(storage)
        if (savedDarkMode === true) {
          document.getElementsByTagName("html")[0].classList.add("theme-dark")
        }
      }
    </script>
    <@resources/>
</head>
<body>
<div class="root">
    <@header.display/>
    <div id="container">
        <nav id="leftColumn" class="sidebar" data-item-type="SECTION" data-item-config='{"defaultSize": 280, "minSize": 200, "maxSize": 400}'>
            <a class="toc--skip-link" href="#main">Skip to content</a>
            <div class="dropdown theme-dark_mobile" data-role="dropdown" id="toc-dropdown">
                <ul role="listbox" id="toc-listbox" class="dropdown--list dropdown--list_toc-list"
                    data-role="dropdown-listbox" aria-label="Table of contents">
                    <div class="dropdown--header">
                            <span>
                                <@template_cmd name="projectName">
                                    ${projectName}
                                </@template_cmd>
                            </span>
                        <button class="button" data-role="dropdown-toggle" aria-label="Close table of contents">
                            <i class="ui-kit-icon ui-kit-icon_cross"></i>
                        </button>
                    </div>
                    <li class="sidebar-demos" role="group" aria-labelledby="sidebar-demos-title">
                        <strong id="sidebar-demos-title">Demos</strong>
                        <@template_cmd name="pathToRoot">
                            <a href="${pathToRoot}demo/js/">Kotlin/JS demo</a>
                            <a href="${pathToRoot}demo/wasm/">Kotlin/Wasm demo</a>
                        </@template_cmd>
                    </li>
                    <div class="sidebar--inner" id="sideMenu"></div>
                </ul>
                <div class="dropdown--overlay"></div>
            </div>
        </nav>
        <div id="resizer" class="resizer" data-item-type="BAR"></div>
        <div id="main" data-item-type="SECTION" role="main">
            <@content/>
            <@footer.display/>
        </div>
    </div>
</div>
</body>
</html>
