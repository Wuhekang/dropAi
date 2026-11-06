"""Namespace-stable XML queries for both python-docx and plain lxml nodes."""

from __future__ import annotations

from lxml import etree


# XPath prefixes belong to the query, not to the input document. In particular,
# a document may use a default namespace, rename `w`, or rebind it in an SDT.
OOXML_NAMESPACES = {
    "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    "m": "http://schemas.openxmlformats.org/officeDocument/2006/math",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}


def xpath(element: etree._Element, expression: str):
    """Evaluate with canonical namespaces without replacing or copying nodes.

    python-docx's BaseOxmlElement.xpath adds its own namespaces but does not
    accept a namespaces argument. Unregistered OOXML tags (SDT, altChunk, math)
    and parsed generic parts are ordinary lxml elements whose xpath supplies no
    namespaces. Calling lxml's base implementation works for both node types,
    preserves node identity for edits, and lets genuine XPath errors surface.
    """
    return etree._Element.xpath(element, expression, namespaces=OOXML_NAMESPACES)
