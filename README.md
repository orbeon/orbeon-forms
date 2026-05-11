# Orbeon Forms

Build, run, and manage web forms on-premises or in the cloud.

Last updated in April 2026.

[![Latest release](https://img.shields.io/github/v/release/orbeon/orbeon-forms?label=latest%20release)](https://github.com/orbeon/orbeon-forms/releases)
[![License](https://img.shields.io/github/license/orbeon/orbeon-forms)](LICENSE.txt)
[![Stack Overflow](https://img.shields.io/badge/Stack%20Overflow-orbeon-F58025)](https://stackoverflow.com/questions/tagged/orbeon)

<p align="center">
    <a href="https://www.orbeon.com/">
        <img src="https://www.orbeon.com/img/home/hero-fb.png" alt="Orbeon Forms Form Builder" width="900">
    </a>
</p>

Orbeon Forms CE is an open-source web form builder and runtime for creating, publishing, and managing complex forms. It helps you manage large numbers of forms, control access, collect data and attachments, generate PDF and Excel files, and integrate forms with existing systems.

- **Build forms visually** with Form Builder, a browser-based form authoring tool.
- **Run responsive forms** with validation, repeated sections, attachments, multi-page navigation, and PDF output.
- **Manage the form lifecycle** from design and testing to publishing, data capture, versioning, and submission.

[Try Orbeon Forms](https://www.orbeon.com/try) |
[Download](https://www.orbeon.com/download) |
[Documentation](https://doc.orbeon.com/) |
[Professional Edition](https://www.orbeon.com/pricing)

## What You Can Build

Orbeon Forms is used around the world in government, banking, healthcare, telecom, education, and more. It is designed for teams that need secure, maintainable, compliant, and integrated forms rather than one-off surveys.

| Area           | Capabilities                                                                                                                          |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------|
| Form authoring | Visual Form Builder, sections, grids, repeated data, validation rules, calculations, visibility rules, and localization               |
| Data capture   | Responsive Form Runner, attachments, multi-page forms, accessibility, data validation, and error summaries                            |
| Integration    | HTTP services, database services, actions, Java and JavaScript APIs, React and Angular components                                     |
| Output         | Automatic PDF generation, PDF templates, Excel export, and submitted data with attachments                                            |
| Operations     | Access control, form definition versioning, data persistence, deployment on Java servlet containers, and cloud or on-premises hosting |

## Editions

Orbeon Forms is open-core and available in two editions:

| Edition                   | Best for                                                                                                                                                     |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Community Edition (CE)    | Open-source use, evaluation, development, and self-supported deployments                                                                                     |
| Professional Edition (PE) | Production deployments requiring commercial support, subscriptions, regular maintenance releases, performance improvements, and advanced enterprise features |

Both editions include Form Builder, Form Runner, and the Orbeon Forms XForms processor. See the [download](https://www.orbeon.com/download) and [pricing](https://www.orbeon.com/pricing) pages for current builds and edition details.

## Quick Start

The fastest way to see Orbeon Forms is the [online demo](https://demo.orbeon.com/demo/fr/). The demo is public, so do not enter private information.

To run Orbeon Forms locally:

- Use [Docker](https://www.orbeon.com/try) to try Orbeon Forms PE with a free trial license.
- Download [Orbeon Forms CE or PE](https://www.orbeon.com/download) and install it in a Java servlet container such as Apache Tomcat.
- Follow the [installation documentation](https://doc.orbeon.com/installation/) for system requirements, servlet container setup, databases, and production configuration.
- Check our comprehensive [documentation](https://doc.orbeon.com/).

## Community and Support

- Ask technical questions on [Stack Overflow](https://stackoverflow.com/questions/ask?tags=orbeon) with the `orbeon` tag.
- Follow Orbeon on [X](https://x.com/orbeon), [Bluesky](https://bsky.app/profile/orbeon.bsky.social), [LinkedIn](https://www.linkedin.com/company/orbeon), or [YouTube](https://www.youtube.com/orbeon).
- Read the [Orbeon Blog](https://www.orbeon.com/blog/).
- Browse the [forum archive](https://groups.google.com/g/orbeon).
- For commercial support and licensing, see [PE subscriptions](https://www.orbeon.com/pricing), [services](https://www.orbeon.com/services), or contact [info@orbeon.com](mailto:info@orbeon.com).

## Source and Issues

The source code of Orbeon Forms CE is available on [GitHub](https://github.com/orbeon/orbeon-forms/). For known issues and RFEs, check the [issue tracker](https://github.com/orbeon/orbeon-forms/issues).

You usually don't have to compile Orbeon Forms yourself. If you want to contribute or build locally, see [Building Orbeon Forms](https://doc.orbeon.com/contributors/building-orbeon-forms).

## Third-Party Software

This product includes:

- software developed by the [Apache Software Foundation](https://www.apache.org/)
- other third-party software listed in [build.sbt](https://github.com/orbeon/orbeon-forms/blob/master/build.sbt)
- Silk Icons, licensed under the Creative Commons Attribution 2.5 License
- [PixelMixer icons](https://iconarchive.com/artist/pixelmixer.html)
- schemas for XSLT 2.0 and XForms 1.1, licensed under the W3C Software License

Please consult the `third-party-licenses` directory for more information about individual licenses.

## Credits

We would like to thank [YourKit, LLC](https://www.yourkit.com/) for kindly supporting open source projects like Orbeon Forms CE with the full-featured [YourKit Java Profiler](https://www.yourkit.com/java/profiler/index.jsp).