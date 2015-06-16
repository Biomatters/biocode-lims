/**
 * Created by frank on 24/06/14.
 */
var errorUrl = "biocode/info/errors";
var projectsUrl = "biocode/projects";
var usersUrl = "biocode/users";
var rolesUrl = "biocode/roles";
var BCIDRootsURL = "biocode/bcid-roots";

var usersPage = "#/users";
var projectPage = "#/projects";
var loggedInUserPage = "/logged-in-user";

var projectMap = null;
var projects = null;

var biocodeControllers = angular.module('biocodeControllers', []);

function updteLevel(node, level) {
    node.level = level;
    for (var i = 0; i < node.children.length; i++) {
        updteLevel(node.children[i], level + 1);
    }

    if (node.children.length > 0) {
        return level + 1;
    } else {
        return level;
    }
}

function initProjects($scope, $http, callback) {
    $http.get(projectsUrl).success(function (data) {
        $scope.projectMap = new Object();
        for (var i = 0; i < data.length; i++) {
            $scope.projectMap[data[i].id] = data[i];
            data[i].parentRoles = new Array();
            data[i].level = 0;
            data[i].cls = 'treegrid-' + data[i].id;
            data[i].children = new Array();
        }

        var maxLevel = 0;
        for (var i = 0; i < data.length; i++) {
            if (data[i].parentProjectID == -1) {
                continue;
            }

            var p = $scope.projectMap[data[i].parentProjectID];
            p.children.push(data[i]);
            var tmp = updteLevel(data[i], p.level + 1);
            if (tmp > maxLevel) {
                maxLevel = tmp;
            }

            data[i].cls = data[i].cls + ' treegrid-parent-' + p.id;
            p.hasChild = 'true';
            p.cls = p.cls + ' treegrid-expanded'
        }

        var tmpArray = new Array();
        for (var i = 0; i <= maxLevel; i++) {
            for (var j = 0; j < data.length; j++) {
                if (data[j].level == i) {
                    tmpArray.push(data[j]);
                }
            }
        }

        $scope.projects = new Array();
        for(var i = 0; i < tmpArray.length; i++) {
            var proj = tmpArray[i];
            var parentId = proj.parentProjectID;
            if (parentId == -1) {
                $scope.projects[i] = proj;
            } else {
                for (var j = 0; j < i; j++) {
                    if ($scope.projects[j].id == parentId) {
                        $scope.projects.splice(j + 1, 0, proj);
                        break;
                    }
                }
            }
        }

        projects = $scope.projects;
        projectMap = $scope.projectMap;
        callback();
    }).error(function(data, status) {
        showAuthenticationError($scope, status, data, "projects", false);
    });
}

function isAssignable(currentProject, optionProject) {
    if (currentProject.id == optionProject.id) {
        return false;
    }

    for (var i = 0; i < currentProject.children.length; i++) {
        if (!isAssignable(currentProject.children[i], optionProject)) {
            return false;
        }
    }

    return true;
}

biocodeControllers.controller('projectListCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('.navbar-nav li').attr('class', '');
        $('li#projects').attr('class', 'active');

        initProjects($scope, $http, function(){});
        $scope.isFirst = true;
        $scope.collapseOrExpend = function(target) {
            if ($scope.isFirst) {
                var nodeId = target.parentNode.parentNode.id;
                $('.tree').treegrid({
                    expanderExpandedClass: 'glyphicon glyphicon-minus',
                    expanderCollapsedClass: 'glyphicon glyphicon-plus'
                });

                var trNode = $('.tree tr');
                if (trNode.treegrid('isExpanded')) {
                    trNode.treegrid('collapse');
                    trNode.treegrid('expand');
                } else if (trNode.treegrid('isCollapsed')) {
                    trNode.treegrid('expand');
                    trNode.treegrid('collapse');
                }

                trNode = $('#' + nodeId);
                if (trNode.treegrid('isExpanded'))
                    trNode.treegrid('collapse');
                else if (trNode.treegrid('isCollapsed'))
                    trNode.treegrid('expand');
            }

            $scope.isFirst = false;
        };

        $scope.onDeleteProjects = function() {
            var inputs = $(":checked") ;
            var delProjects = new Object();
            for (var i = 0; i < inputs.size(); i++) {
                var input = inputs[i];
                var id = $(input.parentNode.parentNode).attr('id');
                delProjects[id] = true;
                $http.delete(projectsUrl + '/' + id).success(function(){
                    initProjects($scope, $http, function(){});
                    $scope.isFirst = true;
                });
            }
        }
    }]);

biocodeControllers.controller('projectDetailCtrl', ['$scope', '$http', '$routeParams',
    function($scope, $http, $routeParams) {
        $('.navbar-nav li').attr('class', '');
        $('li#projects').attr('class', 'active');

        initProjects($scope, $http, function(){
            $scope.project = $scope.projectMap[$routeParams.projectId];
            optionMap = new Object();
            $scope.options = new Array();
            $scope.options.push({label: "--None--", value: -1});

            var data = $scope.projects
            for (var i = 0; i < data.length; i++) {
                if (data[i].id != $routeParams.projectId && isAssignable($scope.project, $scope.projectMap[data[i].id])) {
                    var opt = {label: data[i].name, value: data[i].id};
                    $scope.options.push(opt);
                    optionMap[data[i].id] = opt;
                }
            }

            if ($scope.project.parentProjectID == -1) {
                $scope.parentProjectOpt = $scope.options[0];
            } else {
                $scope.parentProjectOpt = optionMap[$scope.project.parentProjectID];
            }

            $http.get(projectsUrl + '/' + $scope.project.id + '/roles').success(function (data) {
                $scope.userRoles = data;
            }).error(function(data, status) {
                showAuthenticationError($scope, status, data, "projects", false);
            });

            $http.get(rolesUrl).success(function (data) {
                $scope.roles = data;
            });
        });

        $scope.onUpdateProject = function() {
            $scope.project.parentProjectID = $scope.parentProjectOpt.value;
            $http.put(projectsUrl + '/' + $scope.project.id, $scope.project).success(function(){
                alert('Update successful');
            });
        }

        $scope.onAllCheckBox = function(target) {
            $('td input').prop('checked', target.checked);
        }

        $scope.onDeleteUsers = function() {
            var inputs = $(".checkbox") ;
            for (var i = 0; i < inputs.size(); i++) {
                var input = inputs[i];
                if (input.checked === true) {
                    var username = input.parentNode.parentNode.firstElementChild.firstElementChild.innerHTML;

                    $http.delete(projectsUrl + '/' + $scope.project.id + '/roles/' + username).success(function(){
                        initProject();
                    });
                }
            }
        }

        $scope.onDeleteProject = function() {
            $http.delete(projectsUrl + '/' + $scope.project.id).success(function(){
                window.location = projectPage;
            });
        }
    }]);

biocodeControllers.controller('userListCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('.navbar-nav li').attr('class', '');
        $('li#users').attr('class', 'active');
        init();

        function init() {
            $http.get(usersUrl).success(function (data) {
                $scope.users = data;
            }).error(function(data, status) {
                showAuthenticationError($scope, status, data, "users", true);
            });

            $http.get(usersUrl + loggedInUserPage).success(function(data) {
               $scope.loggedInUser = data;
            });
        }

        $scope.onDeleteUser = function() {
            var inputs = $(":checked") ;
            var delUsers = new Object();
            for (var i = 0; i < inputs.size(); i++) {
                var input = inputs[i];
                if (input.id === 'all-project-roles')
                    continue;

                var username = input.parentNode.parentNode.firstElementChild.firstElementChild.innerHTML;
                delUsers[username] = true;
                $http.delete(usersUrl + '/' + username).success(function(){
                });
            }

            var tmp = $scope.users;
            $scope.users = new Array();
            for (var i = 0; i < tmp.length; i++) {
                if (delUsers[tmp[i].username])
                    continue;
                $scope.users.push(tmp[i]);
            }
        }
    }]);

biocodeControllers.controller('userDetailCtrl', ['$scope', '$http', '$routeParams',
    function($scope, $http, $routeParams) {
        $('.navbar-nav li').attr('class', '');
        $('li#users').attr('class', 'active');
        $scope.newPass = '';
        init();

        function init () {
            initProjects($scope, $http, function () {
                $scope.roles = new Array();
                $scope.optionRoles = new Array();

                $http.get(usersUrl + '/' + $routeParams.userId).success(function (data) {
                    $scope.user = data;
                });

                for (var i  = 0; i < $scope.projects.length; i++) {
                    fillRolesForProject($scope.projects[i]);
                }

                $http.get(rolesUrl).success(function (data) {
                    $scope.projectRoles = data;
                    $scope.projectRolesMap = new Object();

                    for (var i = 0; i < data.length; i++) {
                        $scope.projectRolesMap[data[i].id] = data[i];
                    }
                });

                $http.get(usersUrl + loggedInUserPage).success(function(data) {
                    $scope.loggedInUser = data;
                });
            });
        }

        function fillRolesForProject (project) {
            $http.get(projectsUrl + '/' + project.id + '/roles').success(function (data) {
                project.userRoles = data;
                var i = 0;
                for (; i < data.length; i++) {
                    if (data[i].user.username == $scope.user.username) {
                        project.roleName = data[i].role.name;
                        $scope.roles.push(project);
                        break;
                    }
                }

                if (i == data.length) {
                    $scope.optionRoles.push(project);
                }
            });
        }

        $scope.onAllCheckBox = function(target) {
            $('td input.checkbox').prop('checked', target.checked);
        }

        $scope.onDeleteUser = function() {
            $http.delete(usersUrl + '/' + $scope.user.username).success(function(){
                window.location = usersPage;
            });
        }

        $scope.onUpdateUser = function() {
            $http.put(usersUrl + '/' + $scope.user.username, $scope.user).success(function(){
                alert('Update successful');
            });
        }

        $scope.submitPass = function() {
            if ($('#verify')[0].value != $('#passinput')[0].value)
                return;

            var tmpUser = $scope.user;
            tmpUser.password = $('#passinput')[0].value;
            $http.put(usersUrl + '/' + $scope.user.username, tmpUser).success(function(){
                alert('Password update successful');
                $scope.toggleModal();
            });
        }

        $scope.onDeleteRole = function() {
            var inputs = $(".checkbox") ;
            for (var i = 0; i < inputs.size(); i++) {
                var input = inputs[i];
                if (input.checked === true) {
                    var projectId = input.parentNode.parentNode.firstElementChild.firstElementChild.getAttribute('value');

                    var url = projectsUrl + '/' + projectId + '/roles/' + $scope.user.username;
                    $http.delete(url).success(function(){
                        window.location.reload();
                    });
                }
            }
        }

        $scope.onProjectChange = function(projId) {
            $scope.assignedRoles = $scope.projectMap[projId].roles;
        }

        $scope.onAssignRole = function() {
            var url = projectsUrl + '/' + $scope.projId + '/roles/' + $scope.user.username;
            $http.put(url, $scope.projectRolesMap[$scope.roleId]).success(function (data, status, headers) {
                init();
            });
        }

        $scope.myData = {
            link: "http://google.com",
            modalShown: false,
            hello: 'world',
            foo: 'bar'
        }
        $scope.logClose = function() {
            console.log('close!');
        };
        $scope.toggleModal = function() {
            $scope.myData.modalShown = !$scope.myData.modalShown;
        };
    }]);

biocodeControllers.controller('createuserCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('.navbar-nav li').attr('class', '');
        $('li#users').attr('class', 'active');

        $scope.onCreateUser = function() {
            if($scope.user.password != $scope.verify)
                return;

            $http.post(usersUrl, $scope.user).success(function (data, status, headers) {
                window.location = usersPage + '/' + $scope.user.username;
            });
        }
    }]);

biocodeControllers.controller('createProjectCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('.navbar-nav li').attr('class', '');
        $('li#projects').attr('class', 'active');

        $http.get(projectsUrl).success(function (data) {
            $scope.projects = data;
        });

        $scope.onCreateProject = function() {
            $http.post(projectsUrl, $scope.project).success(function (data, status, headers) {
                window.location = projectPage + '/' + data;
            });
        }
    }]);

biocodeControllers.controller('aboutCtrl', ['$scope',
    function($scope) {
        $('.navbar-nav li').attr('class', '');
        $('li#about').attr('class', 'active');
    }]);

biocodeControllers.controller('homeCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('.navbar-nav li').attr('class', '');
        $('li#home').attr('class', 'active');

        $http.get(errorUrl).success(function (data) {
            $('div.errors').html(data);
        });
    }]);

function showAuthenticationError($scope, status, data, resourceName, needAdminAccess) {
    if(status == 401 || status == 403) {
        $scope.errorMessage = "You must be authenticated" + (needAdminAccess ? " as an admin user" : "") + " to view " + resourceName + ".";
    }
    if(data.contains("Jetty")) {
        $scope.errorMessage = data.substring(data.indexOf("<pre>") + 5, data.indexOf("</pre>"));
    } else {
        $scope.errorMessage = data;
    }
}

biocodeControllers.controller('bcidRootsCtrl', ['$scope', '$http',
    function($scope, $http) {
        $('.navbar-nav li').attr('class', '');
        $('li#bcid-roots').attr('class', 'active');
        init();

        function init() {
            $http.get(BCIDRootsURL).success(function (data) {
                $scope.bcidRoots = data;
            }).error(function(data, status) {
                showAuthenticationError($scope, status, data, "bcid roots", false);
            });

            $http.get(usersUrl + loggedInUserPage).success(function(data) {
                $scope.loggedInUser = data;
            });
        }

        $scope.save = function() {
            var bcidRootsTable = document.getElementById("bcidRootsTable");
            var bcidRoots = [];
            var bcidRootsNotUpdated = [];
            for (var i = 1; i < bcidRootsTable.rows.length; i++) {
                var newBCIDRoot = new Object();
                var currRow = bcidRootsTable.rows[i].cells;

                newBCIDRoot.type = currRow.namedItem("type").textContent;
                newBCIDRoot.value = currRow.namedItem("value").textContent;

                bcidRoots.push(newBCIDRoot);
            }

            for (var i = 0; i < bcidRoots.length; i++) {
                $http.put(BCIDRootsURL + '/' + bcidRoots[i].type, JSON.stringify(bcidRoots[i])).error(function(data) {
                    bcidRootsNotUpdated.push(bcidRoots[i].type);
                });
            }

            if (bcidRootsNotUpdated.length > 0) {
                var message = "Could not update BCID Roots:\n";
                for (var i = 0; i < bcidRootsNotUpdated.length; i++) {
                    message = message + "\n" + bcidRootsNotUpdated[i];
                }
                alert(message);
            } else {
                alert("Update successful");
            }
        }
    }]);