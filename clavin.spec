Summary: Clavin
Name: iplant-clavin
Version: 0.1.0
Release: 3
Epoch: 0
BuildArchitectures: noarch
Group: Applications
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
License: BSD
Provides: iplant-clavin
Source0: %{name}-%{version}.tar.gz

%description
iPlant Clavin

%pre
getent group iplant > /dev/null || groupadd -r iplant
getent passwd iplant > /dev/null || useradd -r -g iplant -d /home/iplant -s /bin/bash -c "User for the iPlant services." iplant
exit 0

%prep
%setup -q
mkdir -p $RPM_BUILD_ROOT/usr/local/bin
mkdir -p $RPM_BUILD_ROOT/usr/local/lib/clavin

%build
unset JAVA_OPTS
lein deps
lein compile
lein uberjar


%install
install -m755 clavin $RPM_BUILD_ROOT/usr/local/bin/
install -m644 clavin-1.0.0-SNAPSHOT-standalone.jar $RPM_BUILD_ROOT/usr/local/lib/clavin/

%clean
lein clean
rm -r lib/*

%files
%defattr(0764,iplant,iplant)
%attr(0775, iplant,iplant) /usr/local/bin/clavin
%attr(0644, iplant,iplant) /usr/local/lib/clavin/clavin-1.0.0-SNAPSHOT-standalone.jar

