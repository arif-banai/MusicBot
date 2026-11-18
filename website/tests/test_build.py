from sitecheck import html_pages, local_refs, resolve_ref, soup


def test_site_builds(site_dir):
    assert (site_dir / "index.html").is_file()


def test_no_root_absolute_references(site_dir):
    bad = [(p, r) for p in html_pages(site_dir) for r in local_refs(p) if r.startswith("/")]
    assert bad == []


def test_every_local_reference_resolves(site_dir):
    missing = [
        (str(p.relative_to(site_dir)), r)
        for p in html_pages(site_dir)
        for r in local_refs(p)
        if resolve_ref(p, r, site_dir) is None
    ]
    assert missing == []


def test_content_images_have_alt_text(site_dir):
    bad = [
        (p, i.get("src"))
        for p in html_pages(site_dir)
        for i in soup(p).select(".md-content img")
        if not (i.get("alt") or "").strip()
    ]
    assert bad == []
