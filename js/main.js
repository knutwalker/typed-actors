document.addEventListener('DOMContentLoaded', function() {
  "use strict";
  var postContent = document.getElementById('post-content');
  if (postContent !== undefined) {
    postContent.addEventListener('click', function(e) {
      var target = e.target;
      if (target.tagName === 'H4' && target.id) {
        window.location.hash = target.id;
      }
    });
  }
});
