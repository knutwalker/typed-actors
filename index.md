---
layout: index
---

## {{ site.title }}

{% highlight scala %}
libraryDependencies ++= List({% for module in site.data.modules %}
  "{{ module.organization }}" %% "{{ module.name }}" % "{{ module.version }}"{% unless forloop.last %},{% endunless %}{% endfor %})
{% endhighlight %}
